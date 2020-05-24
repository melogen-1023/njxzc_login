package cn.gk.cloud.model;

public class CheckNeedCaptchaOutBound {
    private boolean needCaptcha;
    private String b64Captcha;

    public CheckNeedCaptchaOutBound(boolean needCaptcha, String b64Captcha) {
        this.needCaptcha = needCaptcha;
        this.b64Captcha = b64Captcha;
    }

    public String getB64Captcha() {
        return b64Captcha;
    }

    public boolean isNeedCaptcha() {
        return needCaptcha;
    }
}
