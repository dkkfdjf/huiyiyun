package com.huiyi.modules.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 科室新增/修改入参。 */
@Data
public class DepartmentSaveDTO {
    @NotNull(message = "请选择所属机构")
    private Long institutionId;
    @NotBlank(message = "科室名称不能为空")
    private String name;
    private Integer sort;   // 可空;新增时 null → 默认 0
}
