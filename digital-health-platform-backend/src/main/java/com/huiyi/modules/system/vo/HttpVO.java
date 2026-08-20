package com.huiyi.modules.system.vo;

import lombok.Data;

/**
 * HTTP 接口运行指标(管理员监控页)。取自 Micrometer 的 http.server.requests(由 Actuator 自动采集每个请求)。
 * - totalRequests:累计请求数
 * - avgLatencyMs / maxLatencyMs:平均 / 最大响应延迟(ms)
 * - errorCount / errorRate:5xx(SERVER_ERROR)错误数与占比(%)
 */
@Data
public class HttpVO {
    private long totalRequests;
    private double avgLatencyMs;
    private double maxLatencyMs;
    private long errorCount;
    private double errorRate;
}
