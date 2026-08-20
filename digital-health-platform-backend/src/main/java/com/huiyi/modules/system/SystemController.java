package com.huiyi.modules.system;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.vo.SystemMetricsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统监控(管理员)。仅返回运行时聚合指标,不含敏感配置。
 * /api/v1/system/** 受 JWT + @RequiresRole(ADMIN) 保护,未在公开白名单中。
 */
@Tag(name = "系统监控")
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;
    private final SystemConfigService systemConfigService;

    @Operation(summary = "系统运行指标(JVM/DB连接池/Redis,管理员)")
    @GetMapping("/metrics")
    @RequiresRole(RoleConstants.ADMIN)
    public R<SystemMetricsVO> metrics() {
        return R.ok(systemService.metrics());
    }

    /** 设置登录滑块验证模式(enabled/pass/lock)。改登录安全配置,记审计日志。
     *  非法 mode 由 CaptchaMode.fromCode 回退 ENABLED,杜绝脏入。 */
    @OperationLog(module = "系统", operation = "修改登录验证配置")
    @Operation(summary = "设置登录滑块验证模式(管理员)")
    @PutMapping("/captcha-mode")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Map<String, String>> setCaptchaMode(@RequestParam String mode) {
        CaptchaMode m = CaptchaMode.fromCode(mode);
        systemConfigService.setCaptchaMode(m, SecurityContextHolder.get().getUserId());
        return R.ok(Map.of("mode", m.getCode()));
    }

    /** 知识库 AI 问答总开关。关闭后 /ask 拒绝、前端精灵提示"已被管理员禁用",用于按需停掉嵌入/LLM 计费。 */
    @OperationLog(module = "系统", operation = "修改知识库开关")
    @Operation(summary = "设置知识库问答开关(管理员)")
    @PutMapping("/kb-enabled")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Map<String, Boolean>> setKbEnabled(@RequestParam boolean enabled) {
        systemConfigService.setKbEnabled(enabled, SecurityContextHolder.get().getUserId());
        return R.ok(Map.of("enabled", enabled));
    }

    /** 游客只读体验入口总开关。关闭后 /auth/guest 拒绝、登录页不渲染游客按钮(默认关,opt-in)。 */
    @OperationLog(module = "系统", operation = "修改游客入口开关")
    @Operation(summary = "设置游客体验入口开关(管理员)")
    @PutMapping("/guest-enabled")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Map<String, Boolean>> setGuestEnabled(@RequestParam boolean enabled) {
        systemConfigService.setGuestEnabled(enabled, SecurityContextHolder.get().getUserId());
        return R.ok(Map.of("enabled", enabled));
    }
}
