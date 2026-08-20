package com.huiyi.modules.auth;

import lombok.Data;

/** 图形验证码响应:captchaKey(verify 时回传)+ base64 图片(data URI,前端可直接 <img :src="img">)。 */
@Data
public class CaptchaVO {
    private String captchaKey;   // 裸 uuid,verify 时与用户输入一起回传
    private String img;          // data:image/png;base64,xxxx
}
