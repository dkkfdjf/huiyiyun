package com.huiyi.modules.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DoctorMapPoint {
    private String name;
    private BigDecimal lng;
    private BigDecimal lat;
    private Long count;     // 该机构医师数
}
