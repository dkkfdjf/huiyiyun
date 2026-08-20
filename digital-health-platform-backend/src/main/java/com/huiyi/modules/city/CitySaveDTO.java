package com.huiyi.modules.city;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 城市新增/修改入参。 */
@Data
public class CitySaveDTO {
    @NotBlank(message = "城市名称不能为空")
    private String name;
    @NotBlank(message = "所属省份不能为空")
    private String province;
    private String regionCode;
}
