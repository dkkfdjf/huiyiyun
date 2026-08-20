package com.huiyi.modules.demand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 医师提交临床用药反馈。drugName 必填(可手填);选已有药品则带 drugId(自动归属其公司)。 */
@Data
public class DemandCreateDTO {
    @NotBlank(message = "药品名称必填")
    private String drugName;

    private Long drugId;            // 选已有药品则带;可空 → 入"未关联池"

    @NotNull(message = "反馈类型必填")
    @Min(1) @Max(2)
    private Integer demandType;     // 1临床用药需求/2临床用量反馈

    @NotNull(message = "需求数量必填")
    @Min(1)
    private Integer qty;

    @NotNull(message = "紧急度必填")
    @Min(1) @Max(2)
    private Integer urgency;        // 1一般/2紧急

    private String remark;          // 需求说明;可空
}
