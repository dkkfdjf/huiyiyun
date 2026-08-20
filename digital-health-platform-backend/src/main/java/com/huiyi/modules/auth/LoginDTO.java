package com.huiyi.modules.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码(可选:请求带才校验;不带则沿用前端滑块作人机校验) */
    private String captcha;
    /** 验证码 key(generate 返回的 captchaKey) */
    private String captchaKey;
    /** 阿里云滑块验证凭据(前端 captchaVerifyCallback 拿到,原样透传,后端二次校验)。
     *  非必填:pass/lock 模式前端故意不发;enabled 模式不发则由 LoginService 判 CAPTCHA_ERROR。
     *  切勿加 @NotBlank——否则 pass 模式会被参数校验挡在模式判定之前,永远登不进。 */
    private String captchaVerifyParam;
}
