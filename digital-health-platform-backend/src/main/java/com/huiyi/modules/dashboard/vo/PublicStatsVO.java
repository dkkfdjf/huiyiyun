package com.huiyi.modules.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * 首页落地页公开概览(免登录)。聚合真实数据替换原先写死的示例数字。
 * - week:本周入库/出库/净增(hero 数字)
 * - trend12w:近 12 周销售/出库趋势(趋势图)
 * - opsFeed:近 N 条真实运营流水(入库+出库)
 * - locations:销售网点经纬度打点
 * - institutions:医疗机构经纬度打点(含在册医师数)
 * - scale:平台规模计数
 */
@Data
public class PublicStatsVO {
    private WeekFlowVO week;
    private List<TrendPoint> trend12w;
    private List<OpsFeedVO> opsFeed;
    private List<DoctorMapPoint> locations;       // 网点:复用 name/lng/lat
    private List<DoctorMapPoint> institutions;    // 机构:复用 name/lng/lat/count(医师数)
    private ScaleVO scale;
}
