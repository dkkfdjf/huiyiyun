package com.huiyi.modules.auth;

import lombok.Data;

/**
 * 游客体验登录入参。游客无需账号密码,仅在 captcha.mode=enabled 时带滑块验真参数。
 * pass 模式下整 body 可空。
 */
@Data
public class GuestLoginDTO {
    /** 阿里云滑块验真串(captcha.mode=enabled 时前端滑块通过后回传)。 */
    private String captchaVerifyParam;
    /** 图形验证码 key(可选,请求带 captcha 才校验)。 */
    private String captchaKey;
    /** 图形验证码(可选)。 */
    private String captcha;
}
