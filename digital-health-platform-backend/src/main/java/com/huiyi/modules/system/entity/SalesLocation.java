package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonView;
import com.huiyi.common.entity.BaseEntity;
import com.huiyi.common.security.view.InternalView;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_location")
public class SalesLocation extends BaseEntity {
    private String name;
    private String address;
    private Long cityId;
    private Long companyId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    @JsonView(InternalView.class)
    private String contactPerson;    // 敏感(联系人):游客不可见
    @JsonView(InternalView.class)
    private String contactPhone;     // 敏感(联系电话):游客不可见
}
