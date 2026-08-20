package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** append-only 流水:不带 deleted/updateBy,不提供 update/delete */
@Data
@TableName("sales_record")
public class SalesRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordNo;
    private Long locationId;
    private Long drugId;
    private Long companyId;
    private Integer qty;
    private BigDecimal price;
    private BigDecimal amount;
    private LocalDateTime saleTime;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
}
