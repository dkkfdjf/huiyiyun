package com.huiyi.modules.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.modules.dashboard.mapper.DashboardMapper;
import com.huiyi.modules.dashboard.vo.ActivityVO;
import com.huiyi.modules.dashboard.vo.DashboardVO;
import com.huiyi.modules.dashboard.vo.TotalStats;
import com.huiyi.modules.system.entity.PharmaCompany;
import com.huiyi.modules.system.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final MedicalInstitutionMapper instMapper;
    private final DrugMapper drugMapper;
    private final PharmaCompanyMapper companyMapper;
    private final SalesLocationMapper locationMapper;

    /** Phase0 仅管理员全局看板;后续阶段按角色分支(company/institution/doctor)。 */
    public DashboardVO adminDashboard() {
        DashboardVO v = new DashboardVO();

        TotalStats t = new TotalStats();
        t.setInstitutionCount(instMapper.selectCount(null));
        t.setDrugCount(drugMapper.selectCount(null));
        t.setCompanyCount(companyMapper.selectCount(
                new LambdaQueryWrapper<PharmaCompany>().eq(PharmaCompany::getAuditStatus, 1)));
        t.setLocationCount(locationMapper.selectCount(null));
        v.setTotals(t);

        v.setDoctorGeo(dashboardMapper.doctorGeo());
        v.setTitleDist(dashboardMapper.titleDistribution());
        v.setDeptDist(dashboardMapper.deptDistribution());
        v.setTrend12w(dashboardMapper.trend12w(12));
        v.setStockAlerts(dashboardMapper.stockAlerts());
        v.setOpsFeed(dashboardMapper.recentOps(20));

        return v;
    }

    /** 最近 N 条操作动态(审计日志 operation_log,append-only 无 deleted 列)。 */
    public List<ActivityVO> recentActivity(int n) {
        return dashboardMapper.recentActivity(n);
    }
}
