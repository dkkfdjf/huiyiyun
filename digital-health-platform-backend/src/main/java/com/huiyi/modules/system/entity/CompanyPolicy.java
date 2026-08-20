package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("company_policy")
public class CompanyPolicy extends BaseEntity {
    private Long companyId;
    private String title;
    private String content;
    private Integer policyType;      // 1医保/2药企/3价格
    private LocalDate effectiveDate;
    private LocalDate expireDate;
}
