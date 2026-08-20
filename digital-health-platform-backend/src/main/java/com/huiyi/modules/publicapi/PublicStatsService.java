package com.huiyi.modules.publicapi;

import com.huiyi.modules.dashboard.mapper.DashboardMapper;
import com.huiyi.modules.dashboard.vo.PublicStatsVO;
import com.huiyi.modules.dashboard.vo.WeekFlowVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 首页落地页公开概览(免登录)。聚合真实数据,替换原先前端写死的示例数字与 Math.random 流水。
 * 不暴露任何行级隔离/商业敏感明细:仅规模计数、当日汇总、12 周趋势、近 12 条流水与网点/机构坐标。
 */
@Service
@RequiredArgsConstructor
public class PublicStatsService {

    private final DashboardMapper dashboardMapper;

    public PublicStatsVO publicStats() {
        PublicStatsVO v = new PublicStatsVO();

        WeekFlowVO w = dashboardMapper.weekFlow();
        if (w == null) w = new WeekFlowVO();
        w.setNetQty(w.getInQty() - w.getOutQty());
        v.setWeek(w);

        v.setTrend12w(dashboardMapper.trend12w(12));
        v.setOpsFeed(dashboardMapper.recentOps(12));
        v.setLocations(dashboardMapper.locationGeo());
        v.setInstitutions(dashboardMapper.institutionGeo());
        v.setScale(dashboardMapper.scaleStats());
        return v;
    }
}
