package com.huiyi.modules.dashboard.vo;

import lombok.Data;

/** 平台规模计数(首页落地页「规模」区段)。 */
@Data
public class ScaleVO {
    private long drugCount;          // 在管药品
    private long locationCount;      // 销售网点
    private long institutionCount;   // 合作医疗机构
    private long doctorCount;        // 在册医师
}
