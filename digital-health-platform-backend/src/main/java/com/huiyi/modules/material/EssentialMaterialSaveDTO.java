package com.huiyi.modules.material;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 必备材料新增/修改入参(管理员维护)。 */
@Data
public class EssentialMaterialSaveDTO {
    @NotBlank(message = "名称不能为空")
    private String name;
    @NotBlank(message = "类别不能为空")
    private String category;
    private String specification;   // 可空
    private String unit;            // 可空
    private String content;         // 可空
}
