package com.huiyi.modules.inventory;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销售流水视图(带药品名/网点名)。 */
@Data
public class SalesRecordVO {
    private Long id;
    private String recordNo;
    private Long drugId;
    private String drugName;
    private Long locationId;
    private String locationName;
    private Integer qty;
    private BigDecimal price;
    private BigDecimal amount;
    private LocalDateTime saleTime;
    private String remark;
}
