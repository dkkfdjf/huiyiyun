package com.huiyi.modules.institution;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 医疗机构新增/修改入参。
 * audit_status 不由客户端传:管理员录入默认正常(1),启停走单独的 status 接口。
 */
@Data
public class InstitutionSaveDTO {
    @NotBlank(message = "机构名称不能为空")
    private String name;
    @NotBlank(message = "机构地址不能为空")
    private String address;
    @NotNull(message = "请选择城市")
    private Long cityId;
    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度范围 -180~180")
    @DecimalMax(value = "180.0", message = "经度范围 -180~180")
    private BigDecimal longitude;
    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度范围 -90~90")
    @DecimalMax(value = "90.0", message = "纬度范围 -90~90")
    private BigDecimal latitude;
    private String contactPerson;
    private String contactPhone;

    // —— 仅"新增"时使用:同时创建 role=2 登录账号并对齐联系人;修改时忽略 ——
    private String username;
    private String password;
    private String realName;   // 账号姓名,新增时同时作为联系人(contact_person)
}
