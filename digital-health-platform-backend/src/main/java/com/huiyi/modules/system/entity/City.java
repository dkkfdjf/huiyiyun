package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("city")
public class City extends BaseEntity {
    private String name;
    private String province;
    private String regionCode;
}
