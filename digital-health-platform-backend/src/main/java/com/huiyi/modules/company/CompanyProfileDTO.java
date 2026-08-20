package com.huiyi.modules.company;

import lombok.Data;

/**
 * 本公司信息自助维护:仅联系方式/地址/许可证号。
 * 公司名称、统一社会信用代码、审核态属身份与合规字段,由管理员维护,公司不可自行修改。
 */
@Data
public class CompanyProfileDTO {
    private String contactPerson;
    private String contactPhone;
    private String address;
    private String licenseNo;
}
