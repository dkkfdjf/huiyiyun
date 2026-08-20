package com.huiyi.modules.inventory;

import lombok.Data;

/** mapper 分组求和的中间结果:药品 × 网点 → 数量。供台账批量装配累计入库/销售。 */
@Data
public class LedgerFlowVO {
    private Long drugId;
    private Long locationId;
    private Integer qty;

    /** 组合键,把分组结果装进 Map 以 O(1) 取值。 */
    public static String key(Long drugId, Long locationId) {
        return drugId + "|" + locationId;
    }

    public String key() {
        return key(drugId, locationId);
    }
}
