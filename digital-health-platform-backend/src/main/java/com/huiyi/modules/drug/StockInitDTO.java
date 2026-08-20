package com.huiyi.modules.drug;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 铺货初始化(UC-A04-1)入参。 */
@Data
public class StockInitDTO {
    @NotNull(message = "销售地点不能为空")
    private Long locationId;
    @NotNull(message = "药品不能为空")
    private Long drugId;
    @NotNull(message = "初始库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stockQty;
    @NotNull(message = "售价不能为空")
    @DecimalMin(value = "0.01", message = "售价必须大于0")
    private BigDecimal price;
    @Min(value = 0, message = "预警阈值不能为负")
    private Integer threshold;        // 可空 → 默认 0
}
