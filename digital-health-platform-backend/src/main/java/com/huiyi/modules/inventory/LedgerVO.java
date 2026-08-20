package com.huiyi.modules.inventory;

import lombok.Data;

/**
 * 进销存台账行(药品 × 销售网点 维度的对账汇总)。
 * 累计入库 = SUM(replenishment_order.qty);累计销售 = SUM(sales_record.qty);
 * 当前库存 = drug_stock.stock_qty(实时余额,不受时间窗口影响——库存是时点数,进出是期间流量)。
 * balanced:累计入库 − 累计销售 == 当前库存;仅在未加时间过滤(查全历史)时具有对账意义,
 * 加时间窗口时 totalIn/totalOut 为窗口内流量,balanced 仅供参考。
 */
@Data
public class LedgerVO {
    private Long drugId;
    private String drugName;
    private Long locationId;
    private String locationName;
    private Long companyId;
    private Integer totalIn;        // 累计入库量(窗口内)
    private Integer totalOut;       // 累计销售量(窗口内)
    private Integer currentStock;   // 当前库存(实时余额)
    private Integer windowDiff;     // 窗口净流量 = 累计入库 − 累计销售
    private boolean balanced;       // 累计入库 − 累计销售 == 当前库存(仅查全历史时有对账意义)
}
