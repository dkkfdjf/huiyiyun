package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 必备材料:患者办理报销、特殊病种等事项所需携带的资料清单
 * (如门诊报销、住院报销、特殊病种办理、糖尿病必备材料等)。
 * 管理员统一维护,全角色查阅。无租户隔离的全局参照数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("essential_material")
public class EssentialMaterial extends BaseEntity {
    private String name;             // 标题(如"门诊报销")
    private String category;         // 类别(报销类/慢病类/特殊病种类…)
    private String specification;    // 预留字段,报销资料一般不用
    private String unit;             // 预留字段,报销资料一般不用
    private String content;          // 携带资料说明/详情(如"门诊发票、合作医疗证历本")
}
