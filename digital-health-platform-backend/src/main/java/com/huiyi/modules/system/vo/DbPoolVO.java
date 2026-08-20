package com.huiyi.modules.system.vo;

import lombok.Data;

/** 数据库连接池指标(当前为 HikariCP)。 */
@Data
public class DbPoolVO {
    private String type;     // HikariCP
    private int active;      // 活跃连接(在用)
    private int idle;        // 空闲连接
    private int total;       // 总连接(active+idle)
    private int waiting;     // 等待获取连接的线程
    private int max;         // 连接上限(maximumPoolSize)
}
