package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonView;
import com.huiyi.common.entity.BaseEntity;
import com.huiyi.common.security.view.InternalView;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pharma_company")
public class PharmaCompany extends BaseEntity {
    private String name;
    @JsonView(InternalView.class)
    private String creditCode;       // 敏感(信用代码):游客不可见
    @JsonView(InternalView.class)
    private String licenseNo;        // 敏感(许可证号):游客不可见
    @JsonView(InternalView.class)
    private String contactPerson;    // 敏感(联系人):游客不可见
    @JsonView(InternalView.class)
    private String contactPhone;     // 敏感(联系电话):游客不可见
    private String address;
    private Integer auditStatus;     // 0待审核/1正常/2驳回/3停用
    private String auditRemark;
    private LocalDateTime auditTime;
}
