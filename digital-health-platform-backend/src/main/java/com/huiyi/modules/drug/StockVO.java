package com.huiyi.modules.drug;

import lombok.Data;

import java.math.BigDecimal;

/** 库存视图(带药品名/网点名,供列表与预警共用)。 */
@Data
public class StockVO {
    private Long id;
    private Long drugId;
    private String drugName;
    private Long locationId;
    private String locationName;
    private Long companyId;
    private String companyName;
    private Integer stockQty;
    private BigDecimal price;
    private Integer threshold;
    private Integer version;
    private Boolean alert;            // stockQty <= threshold
}
