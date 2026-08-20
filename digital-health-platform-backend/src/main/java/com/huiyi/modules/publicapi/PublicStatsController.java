package com.huiyi.modules.publicapi;

import com.huiyi.common.result.R;
import com.huiyi.modules.dashboard.vo.PublicStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开数据(免登录)。路径 /api/v1/public/** 已在 JwtAuthFilter 白名单放行。
 * 仅返回聚合/脱敏数据,供首页落地页渲染真实概览。
 */
@Tag(name = "公开数据")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicStatsController {

    private final PublicStatsService publicStatsService;

    @Operation(summary = "首页落地页公开概览(免登录:今日进出/趋势/流水/网点机构打点/规模)")
    @GetMapping("/stats")
    public R<PublicStatsVO> stats() {
        return R.ok(publicStatsService.publicStats());
    }
}
