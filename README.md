# njxzc_login

##### 第一步： 检查帐户是否需要验证

```java
EduAccountAuthenticated eduAccountAuthenticated = new EduAccountAuthenticated(jedis,username);

CheckNeedCaptchaOutBound checkNeedCaptchaOutBound = eduAccountAuthenticated.getCheckNeedCaptcha();
 
// 验证码b64字符串:checkNeedCaptchaOutBound.getCheckNeedCaptcha();
// 是否需要验证码 :checkNeedCaptchaOutBound.isNeedCaptcha();
```

##### 第二步：登录

```java
EduAccountAuthenticated eduAccountAuthenticated = new EduAccountAuthenticated(
                jedis,
                username,password,captcha
        );

eduAccountAuthenticated.login()

// 获取登录结果: eduAccountAuthenticated.isAuthenticated()
```

