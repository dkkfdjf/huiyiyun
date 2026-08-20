package com.huiyi.modules.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 销售/出库入参(UC-A04-2)。 */
@Data
public class SaleDTO {
    @NotNull(message = "药品不能为空")
    private Long drugId;
    @NotNull(message = "销售网点不能为空")
    private Long locationId;
    @NotNull(message = "销售数量不能为空")
    @Min(value = 1, message = "销售数量必须大于0")
    private Integer qty;
    @DecimalMin(value = "0.01", message = "售价必须大于0")
    private BigDecimal price;          // 可空 → 取库存售价
    private String remark;             // 可空
}
