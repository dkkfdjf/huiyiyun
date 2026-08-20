package com.huiyi.modules.cache;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 缓存监控:仪表盘「缓存监控」面板 / 顶栏指示灯轮询本接口展示 Redis 实时状态(在线/缓存条数/命中/明细)。
 * 权限:仅管理员(@RequiresRole)——Redis 命中数、缓存明细属运维指标,不向业务角色(医师/药企/机构)暴露。
 */
@Tag(name = "缓存监控")
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
public class CacheMonitorController {

    private final CacheMonitorService cacheMonitorService;

    @Operation(summary = "Redis 缓存实时状态(仅管理员)")
    @GetMapping("/stats")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Map<String, Object>> stats() {
        return R.ok(cacheMonitorService.stats());
    }

    @OperationLog(module = "缓存监控", operation = "清空缓存")
    @Operation(summary = "清空全部业务缓存(仅管理员)")
    @PostMapping("/evict")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> evict() {
        cacheMonitorService.clearAll();
        return R.ok();
    }
}
