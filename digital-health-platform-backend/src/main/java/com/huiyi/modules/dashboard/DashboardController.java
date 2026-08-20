package com.huiyi.modules.dashboard;

import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.modules.dashboard.vo.ActivityVO;
import com.huiyi.modules.dashboard.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "看板")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "角色化看板(Phase0:管理员全局)")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN})
    public R<DashboardVO> dashboard() {
        return R.ok(dashboardService.adminDashboard());
    }

    @Operation(summary = "近期操作动态(管理员,最近 20 条)")
    @GetMapping("/activity")
    @RequiresRole({RoleConstants.ADMIN})
    public R<List<ActivityVO>> activity() {
        return R.ok(dashboardService.recentActivity(20));
    }
}
