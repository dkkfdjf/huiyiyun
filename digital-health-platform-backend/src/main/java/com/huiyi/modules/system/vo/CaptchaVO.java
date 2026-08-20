package com.huiyi.modules.system.vo;

import lombok.Data;

/**
 * 阿里云滑块验证码「真实调用次数」统计(管理员监控页,估算阿里云验证码费用)。
 * 计数点 = AliyunCaptchaService.verify() 收到非空凭据真正调用阿里云时(CaptchaMetrics.increment)。
 * 含今日/本月/累计;与登录尝试次数解耦(pass/lock/凭据错误不计数)。
 */
@Data
public class CaptchaVO {
    private long today;     // 今日调用次数(≈今日登录尝试数)
    private long month;     // 本月调用次数
    private long total;     // 累计调用次数
}
