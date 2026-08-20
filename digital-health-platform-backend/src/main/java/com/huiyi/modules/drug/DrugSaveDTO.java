package com.huiyi.modules.drug;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 药品新增/修改入参;companyId 取当前登录用户,不由客户端传。 */
@Data
public class DrugSaveDTO {
    @NotBlank(message = "药品名称不能为空")
    private String name;
    private String specification;     // 规格
    private String dosageForm;        // 剂型
    @NotBlank(message = "批准文号不能为空")
    private String approvalNo;
    private String unit;              // 单位(盒/瓶/支)
    private String producer;          // 生产企业
    private Integer status;           // 0下架/1上架;新增时 null → 默认 1
}
