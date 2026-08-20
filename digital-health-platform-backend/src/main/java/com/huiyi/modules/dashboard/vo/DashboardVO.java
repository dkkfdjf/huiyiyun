package com.huiyi.modules.dashboard.vo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardVO {
    private TotalStats totals;
    private List<NameValue> titleDist;       // 职称分布(饼)
    private List<NameValue> deptDist;        // 科室分布(柱)
    private List<DoctorMapPoint> doctorGeo;  // 医师地理分布(按机构聚合)
    private List<TrendPoint> trend12w;       // 近12周趋势
    private List<StockAlertVO> stockAlerts;  // 库存预警
    private List<OpsFeedVO> opsFeed;         // 运营流水
}
