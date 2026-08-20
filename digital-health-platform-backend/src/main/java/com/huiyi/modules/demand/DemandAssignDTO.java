package com.huiyi.modules.demand;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理员指派反馈归属公司(把未关联反馈派给某药企)。 */
@Data
public class DemandAssignDTO {
    @NotNull(message = "公司必填")
    private Long companyId;
}
