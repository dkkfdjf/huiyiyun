package com.huiyi.modules.drug;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 库存维护入参:售价/阈值可部分更新,version 必传(乐观锁)。库存变更走出库/补货,保证台账可平。 */
@Data
public class StockUpdateDTO {
    @NotNull(message = "id不能为空")
    private Long id;
    @NotNull(message = "版本号不能为空")
    private Integer version;          // 乐观锁:客户端读取时的版本
    @DecimalMin(value = "0.01", message = "售价必须大于0")
    private BigDecimal price;         // null = 不改
    @Min(value = 0, message = "预警阈值不能为负")
    private Integer threshold;        // null = 不改
}
