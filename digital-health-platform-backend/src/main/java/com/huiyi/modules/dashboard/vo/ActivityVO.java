package com.huiyi.modules.dashboard.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 看板「近期动态」条目:源自审计日志 operation_log。 */
@Data
public class ActivityVO {
    private Long id;
    private String module;
    private String operation;
    /** 操作人:优先 user.real_name,缺失时退回 operation_log.username。 */
    private String actor;
    private LocalDateTime time;
}
