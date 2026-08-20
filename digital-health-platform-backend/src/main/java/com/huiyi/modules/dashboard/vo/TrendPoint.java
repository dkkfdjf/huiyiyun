package com.huiyi.modules.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrendPoint {
    private String week;            // YEARWEEK,如 202626
    private BigDecimal salesAmount; // 销售额(元)
    private Long outboundQty;       // 出库量(件)
}
