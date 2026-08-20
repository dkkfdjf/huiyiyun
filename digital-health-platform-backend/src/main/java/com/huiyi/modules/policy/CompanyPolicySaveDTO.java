package com.huiyi.modules.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 政策新增/修改入参。
 * companyId 仅管理员须指定;公司角色由后端强制覆盖为本人(防越权伪造归属)。
 */
@Data
public class CompanyPolicySaveDTO {
    private Long companyId;       // 可空:公司角色忽略;管理员新增时必填(服务层校验)
    @NotNull(message = "请选择政策类型")
    private Integer policyType;   // 1医保/2药企/3价格
    @NotBlank(message = "标题不能为空")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;
    @NotNull(message = "请选择生效日期")
    private LocalDate effectiveDate;
    private LocalDate expireDate; // 可空:长期有效
}
