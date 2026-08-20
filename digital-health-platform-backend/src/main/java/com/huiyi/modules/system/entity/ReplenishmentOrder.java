package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** append-only 流水:不带 deleted/updateBy,不提供 update/delete */
@Data
@TableName("replenishment_order")
public class ReplenishmentOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long locationId;
    private Long drugId;
    private Long companyId;
    private Integer qty;
    private LocalDateTime inTime;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
}
