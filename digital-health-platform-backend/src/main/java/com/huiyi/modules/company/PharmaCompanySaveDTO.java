package com.huiyi.modules.company;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 医药公司新增/修改入参。
 * audit_status 不由客户端传:管理员录入默认正常(1),审核/启停走单独的 audit 接口。
 */
@Data
public class PharmaCompanySaveDTO {
    @NotBlank(message = "公司名称不能为空")
    private String name;
    @NotBlank(message = "统一社会信用代码不能为空")
    private String creditCode;
    private String licenseNo;
    private String contactPerson;
    private String contactPhone;
    private String address;

    // —— 仅"新增"时使用:同时创建 role=1 登录账号并对齐联系人;修改时忽略 ——
    private String username;
    private String password;
    private String realName;   // 账号姓名,新增时同时作为联系人(contact_person)
}
