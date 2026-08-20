package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("drug_stock")
public class DrugStock extends BaseEntity {
    private Long drugId;
    private Long locationId;
    private Long companyId;          // 冗余,便于行级隔离
    private Integer stockQty;
    private BigDecimal price;
    private Integer threshold;       // stock_qty<=threshold 触发预警
    @Version
    private Integer version;         // 乐观锁(编辑售价/阈值用)
}
