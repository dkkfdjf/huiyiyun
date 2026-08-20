package com.huiyi.modules.dashboard.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpsFeedVO {
    private LocalDateTime time;
    private String event;
    private Long delta;     // 入库正、出库负
    private String type;    // in / out
}
