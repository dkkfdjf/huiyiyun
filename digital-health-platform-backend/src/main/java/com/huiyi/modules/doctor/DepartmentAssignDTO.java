package com.huiyi.modules.doctor;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 分配/调配科室入参。 */
@Data
public class DepartmentAssignDTO {
    @NotNull(message = "请选择目标科室")
    private Long departmentId;
}
