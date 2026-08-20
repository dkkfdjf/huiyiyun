package com.huiyi.modules.dashboard.vo;

import lombok.Data;

@Data
public class StockAlertVO {
    private String drugName;
    private String spec;
    private String companyName;
    private String locationName;
    private Integer stockQty;
    private Integer threshold;
}
