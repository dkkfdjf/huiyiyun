package com.huiyi.modules.system.vo;

import lombok.Data;

/** Redis 连通性与延迟。 */
@Data
public class RedisVO {
    private boolean online;
    private long latencyMs;  // ping 往返耗时
    private String error;    // 未连接时的异常信息(仅 online=false 时有值)
}
