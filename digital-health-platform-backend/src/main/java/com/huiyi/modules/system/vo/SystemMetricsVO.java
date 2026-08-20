package com.huiyi.modules.system.vo;

import lombok.Data;

/** 系统运行指标(管理员监控页)。聚合 JVM/DB连接池/Redis/HTTP 四块。 */
@Data
public class SystemMetricsVO {
    private JvmVO jvm;
    private DbPoolVO db;
    private RedisVO redis;
    private HttpVO http;   // HTTP 接口运行指标(Micrometer http.server.requests)
    private CaptchaVO captcha;   // 阿里云滑块验证码调用次数(≈登录次数,估费用)
    private KbVO kb;   // 本地知识库向量库占用(向量数/维度/内存/模型),供监控
}
