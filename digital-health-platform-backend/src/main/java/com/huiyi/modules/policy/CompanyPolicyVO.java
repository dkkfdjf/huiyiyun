package com.huiyi.modules.policy;

import lombok.Data;

import java.time.LocalDate;

/**
 * 药企公告 VO (含企业名称)
 */
@Data
public class CompanyPolicyVO {
    private Long id;
    private Long companyId;
    private String companyName;     // 企业名称
    private String title;
    private String content;
    private Integer policyType;     // 1医保/2药企/3价格
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private String createTime;
    private String updateTime;
}
