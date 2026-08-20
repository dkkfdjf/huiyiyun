package com.huiyi.modules.dashboard.vo;

import lombok.Data;

/** 本周(ISO 周,周一 00:00 起 7 天)入库/出库/净增件数 + 本周销售额。供首页落地页 hero 数字。 */
@Data
public class WeekFlowVO {
    private long inQty;    // 本周入库(replenishment_order)
    private long outQty;   // 本周出库(sales_record)
    private long netQty;   // 净增 = inQty - outQty(service 计算)
    private long salesAmount;  // 本周销售额(sales_record.amount 本周汇总)
}
