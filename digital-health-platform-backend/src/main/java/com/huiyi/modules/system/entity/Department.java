package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("department")
public class Department extends BaseEntity {
    private Long institutionId;
    private String name;
    private Integer sort;
}
