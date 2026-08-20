package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonView;
import com.huiyi.common.entity.BaseEntity;
import com.huiyi.common.security.view.InternalView;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("medical_institution")
public class MedicalInstitution extends BaseEntity {
    private String name;
    private String address;
    private Long cityId;
    private BigDecimal longitude;    // GCJ-02
    private BigDecimal latitude;
    @JsonView(InternalView.class)
    private String contactPerson;    // 敏感(联系人):游客不可见
    @JsonView(InternalView.class)
    private String contactPhone;     // 敏感(联系电话):游客不可见
    private Integer auditStatus;     // 0待审核/1正常/2驳回/3停用
    private String auditRemark;
    private LocalDateTime auditTime;
}
