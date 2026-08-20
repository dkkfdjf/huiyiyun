package com.huiyi.modules.inventory;

import lombok.Data;

/**
 * 对账修正结果:以当前库存为准,补写「历史维护对账修正」流水后的统计。
 * surplus=盘盈(diff>0,补入库)条数;deficit=盘亏(diff<0,补出库)条数;skipped=已平账、跳过的行数。
 */
@Data
public class ReconcileVO {
    private int surplus;   // 盘盈:库存多于流水,补 replenishment_order
    private int deficit;   // 盘亏:库存少于流水,补 sales_record(按挂牌价记账)
    private int skipped;   // diff==0,台账已平,无需修正
}
