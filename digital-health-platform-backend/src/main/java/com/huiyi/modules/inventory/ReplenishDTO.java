package com.huiyi.modules.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 补货/入库入参(UC-A04-3)。 */
@Data
public class ReplenishDTO {
    @NotNull(message = "药品不能为空")
    private Long drugId;
    @NotNull(message = "销售网点不能为空")
    private Long locationId;
    @NotNull(message = "补货数量不能为空")
    @Min(value = 1, message = "补货数量必须大于0")
    private Integer qty;
    private String remark;             // 可空
}
