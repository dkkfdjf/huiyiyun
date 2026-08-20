package com.huiyi.modules.system.vo;

import lombok.Data;

/** JVM 运行时指标。堆内存、线程、运行时长、CPU/系统负载。 */
@Data
public class JvmVO {
    private long heapUsed;        // 已用堆 = totalMemory - freeMemory
    private long heapCommitted;   // 当前分配(totalMemory)
    private long heapMax;         // 最大堆(maxMemory)
    private int threads;          // 活跃线程
    private int daemonThreads;    // 守护线程
    private long uptimeMs;        // JVM 运行时长
    private int cpuCores;
    private double systemLoad;    // 系统平均负载(1min);Windows 常返回 -1 表示不可用
}
