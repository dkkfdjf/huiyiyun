package com.huiyi.modules.inventory;

import lombok.Data;

import java.time.LocalDateTime;

/** 补货/入库流水视图(带药品名/网点名)。 */
@Data
public class ReplenishOrderVO {
    private Long id;
    private String orderNo;
    private Long drugId;
    private String drugName;
    private Long locationId;
    private String locationName;
    private Integer qty;
    private LocalDateTime inTime;
    private String remark;
}
