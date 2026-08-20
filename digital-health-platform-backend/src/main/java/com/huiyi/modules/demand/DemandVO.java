package com.huiyi.modules.demand;

import com.huiyi.modules.system.entity.DrugDemand;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 反馈分页 VO:在原始反馈字段上带回 反馈医师 / 医疗机构 / 归属公司 名称,
 * 前端列表直接展示"是谁/哪家医院报的",无需再各自解析 ID。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DemandVO extends DrugDemand {
    private String doctorName;       // 反馈医师姓名(可空)
    private String institutionName;  // 医师所属医疗机构(可空)
    private String companyName;      // 归属药企(可空 → 未关联池)
}
