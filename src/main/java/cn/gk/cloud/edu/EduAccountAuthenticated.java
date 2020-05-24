package cn.gk.cloud.edu;

import cn.gk.cloud.model.CheckNeedCaptchaOutBound;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import redis.clients.jedis.Jedis;
import cn.gk.cloud.conf.EduUrl;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EduAccountAuthenticated {
    static class EduAccount implements Serializable {
        private static final long serialVersionUID = -1037432878077358887L;
        private String cookies;
        private String[] loginParams;
    }

    private EduAccount eduAccount;
    private Jedis jedis;
    private String number, password, captcha;
    private boolean authenticated = false;
    private String b64Captcha;
    private boolean needCaptcha = false;


    public boolean isAuthenticated() {
        return authenticated;
    }


    public EduAccountAuthenticated(Jedis jedis, String number) throws Exception {
        this.eduAccount = new EduAccount();
        this.jedis = jedis;
        this.number = number;
        this.init();
    }

    public EduAccountAuthenticated(Jedis jedis, String number, String password, String captcha) {
        this.jedis = jedis;
        this.number = number;
        this.password = password;
        this.captcha = captcha;
    }

    public EduAccountAuthenticated(Jedis jedis, String number, String password) {
        this(jedis, number, password, null);
    }

    public CheckNeedCaptchaOutBound getCheckNeedCaptcha() {
        return new CheckNeedCaptchaOutBound(this.needCaptcha, this.b64Captcha);
    }

    private void init() throws Exception{
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(EduUrl.LOGIN_PAGE_URL);
        httpGet.setHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.92 Safari/537.36");

            /*
            抓取页面的登录数据，以及保存请求头
             */
        CloseableHttpResponse response = httpClient.execute(httpGet);
        HttpEntity httpEntity = response.getEntity();
        this.eduAccount.loginParams = parseLoginPage(EntityUtils.toString(httpEntity, "utf-8"));
        for (Header header :
                response.getAllHeaders()) {
            if ("Set-Cookie".equals(header.getName())) {
                parseSetCookie(header.getValue());
            }
        }
            /*
            保存至redis
             */
        HttpGet httpGet1 = new HttpGet(EduUrl.LOGIN_NEED_CAPTCHA_URL + "?username=" + this.number);
        HttpEntity httpEntity1 = httpClient.execute(httpGet1).getEntity();
        this.needCaptcha = (Boolean.parseBoolean(EntityUtils.toString(httpEntity1, "utf-8").trim()));
        if (this.needCaptcha) {
            readImage(httpClient);
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(eduAccount);
        String res = this.jedis.setex(this.number.getBytes(), EduUrl.EDU_ACCOUNT_REDIS_EXPIRE_TIME, byteArrayOutputStream.toByteArray());
        if (!"OK".equals(res)){throw new Exception("redis设置用户失败");}
    }

    private void parseSetCookie(String content) {
//        content += ";";
//        String pattern1 = "(.*?=.*?;)*";
//        Pattern r1 = Pattern.compile(pattern1);
//        Matcher matcher1 = r1.matcher(content);
//        if (!matcher1.find()) return;
//        String pattern2 = "(.*?)=(.*?);";
//        for (int i = 0; i <= matcher1.groupCount(); i++
//        ) {
//            Pattern r = Pattern.compile(pattern2);
//            Matcher matcher = r.matcher(matcher1.group(i));
//            if (!matcher.find()) return;
//            for (int j = 1; j <= matcher.groupCount(); j += 2) {
//                eduAccount.headers.put(matcher.group(j), matcher.group(j + 1));
//            }
//        }
        if (eduAccount.cookies == null) {
            eduAccount.cookies = "";
        }
        if (!"".equals(eduAccount.cookies)) {
            eduAccount.cookies += ";";
        }
        eduAccount.cookies += content;
    }

    private String[] parseLoginPage(String pageContent) {
        String pattern = "<input.type=\"hidden\".name=\"lt\".value=\"(.*?)\"/>\\s*?<input.type=\"hidden\".name=\"dllt\".value=\"(.*?)\"/>\\s*?<input.type=\"hidden\".name=\"execution\".value=\"(.*?)\"/>\\s*?<input.type=\"hidden\".name=\"_eventId\".value=\"(.*?)\"/>\\s*?<input.type=\"hidden\".name=\"rmShown\".value=\"(.*?)\">";
        Pattern r = Pattern.compile(pattern);
        Matcher matcher = r.matcher(pageContent);
        boolean findRes = matcher.find();
        if (!findRes) return null;
        return new String[]{matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5)};

    }

    private void readImage(CloseableHttpClient httpClient) throws IOException {
        HttpGet httpGet = new HttpGet(EduUrl.LOGIN_CAPTCHA_URL);
        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
        HttpEntity httpEntity = httpResponse.getEntity();
        InputStream inputStream = httpEntity.getContent();
        Base64.Encoder encoder = Base64.getEncoder();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 4);
        int len;
        byte[] bytes = new byte[1024];
        while ((len = inputStream.read(bytes)) != -1) {
            byteBuffer.put(bytes, 0, len);
        }
        this.b64Captcha = encoder.encodeToString(byteBuffer.array());
    }


    private void reload() throws Exception {
        byte[] bytes = this.jedis.get(this.number.getBytes());
        if (bytes == null) throw new Exception("redis中没有该用户");
        this.jedis.del(this.number.getBytes());
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        this.eduAccount = (EduAccount) objectInputStream.readObject();
    }

    public void login() throws Exception {
        reload();
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(EduUrl.LOGIN_PAGE_URL);
        /*
        设置请求头
         */

        httpPost.setHeader("Cookie", this.eduAccount.cookies);
        httpPost.setHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.92 Safari/537.36");
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
        List<NameValuePair> nameValuePairList = new ArrayList<NameValuePair>();
        nameValuePairList.add(new BasicNameValuePair("username", this.number));
        nameValuePairList.add(new BasicNameValuePair("password", this.password));
        if (captcha != null) nameValuePairList.add(new BasicNameValuePair("captchaResponse", this.captcha));
        nameValuePairList.add(new BasicNameValuePair("lt", this.eduAccount.loginParams[0]));
        nameValuePairList.add(new BasicNameValuePair("dllt", this.eduAccount.loginParams[1]));
        nameValuePairList.add(new BasicNameValuePair("execution", this.eduAccount.loginParams[2]));
        nameValuePairList.add(new BasicNameValuePair("_eventId", this.eduAccount.loginParams[3]));
        nameValuePairList.add(new BasicNameValuePair("rmShown", this.eduAccount.loginParams[4]));
        UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(
                nameValuePairList,
                "UTF-8"
        );
        httpPost.setEntity(urlEncodedFormEntity);
        CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
        this.authenticated = (302 == httpResponse.getStatusLine().getStatusCode());
    }


}

