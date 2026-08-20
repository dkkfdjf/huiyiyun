package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 临床用药反馈(M8)。
 * status:0待处理/1处理中/2已满足/3已驳回/4已撤回。
 * company_id 为空 → "未关联临床反馈池",由管理员指派。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("drug_demand")
public class DrugDemand extends BaseEntity {
    private Long doctorId;          // 提交医师 → doctor.id(仅本人可见)
    private Long institutionId;     // 医师机构
    private String drugName;        // 必填,可手填
    private Long drugId;            // 关联药品;可空
    private Long companyId;         // 关联公司;空→未关联池
    private Integer demandType;     // 1临床用药需求/2临床用量反馈
    private Integer qty;            // 需求数量
    private Integer urgency;        // 1一般/2紧急
    private Integer status;         // 0待处理/1处理中/2已满足/3已驳回/4已撤回
    private String remark;          // 需求说明
    private String reply;           // 公司回复
    private Long handlerId;         // 处理人 → user.id
    private LocalDateTime handleTime;
}
