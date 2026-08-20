package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonView;
import com.huiyi.common.entity.BaseEntity;
import com.huiyi.common.security.view.InternalView;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doctor")
public class Doctor extends BaseEntity {
    private Long userId;             // 关联账号(1:1)
    private String name;
    private Long institutionId;
    private Long departmentId;
    private String title;            // 医师/主治/副主任/主任
    @JsonView(InternalView.class)
    private String phone;            // 敏感:游客不可见
    @JsonView(InternalView.class)
    private String email;            // 敏感:游客不可见
    @TableField(exist = false)
    private String institutionName;  // 归属机构名(getMe 时 join 填充,非 DB 列;医生端"我的信息/反馈"展示用)
}
