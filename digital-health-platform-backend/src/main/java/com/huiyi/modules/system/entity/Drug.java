package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("drug")
public class Drug extends BaseEntity {
    private String name;
    private String specification;
    private String dosageForm;
    private Long companyId;
    private String approvalNo;
    private String unit;
    private String producer;
    private Integer status;          // 0下架/1上架
}
