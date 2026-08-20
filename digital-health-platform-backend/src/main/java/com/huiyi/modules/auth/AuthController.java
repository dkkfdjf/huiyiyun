package com.huiyi.modules.auth;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.R;
import com.huiyi.modules.system.SystemConfigService;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final CaptchaService captchaService;
    private final SystemConfigService systemConfigService;

    @OperationLog(module = "认证", operation = "登录")
    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest req) {
        return R.ok(loginService.login(dto, clientIp(req), req.getHeader("User-Agent")));
    }

    /** 游客体验登录:免账号,签发 GUEST 只读临时令牌。受 guest.enabled + captcha.mode + IP 限流约束。
     *  不记 @OperationLog(游客无真实账号;成功/失败均已落 login_log,审计同源)。 */
    @Operation(summary = "游客体验登录(免账号,签发 GUEST 只读临时令牌)")
    @PostMapping("/guest")
    public R<LoginVO> guest(@RequestBody(required = false) GuestLoginDTO dto, HttpServletRequest req) {
        return R.ok(loginService.guestLogin(dto == null ? new GuestLoginDTO() : dto,
                clientIp(req), req.getHeader("User-Agent")));
    }

    /** 游客体验入口是否开放(免登录探活):登录页据此决定是否渲染"游客体验"按钮。 */
    @Operation(summary = "游客体验入口是否开放(免登录)")
    @GetMapping("/guest-enabled")
    public R<Map<String, Boolean>> guestEnabled() {
        return R.ok(Map.of("enabled", systemConfigService.isGuestEnabled()));
    }

    @Operation(summary = "获取图形验证码(设计 §6.3)")
    @PostMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(captchaService.generate());
    }

    /** 当前登录滑块验证模式(登录页探活,免登录)。返回 enabled/pass/lock。
     *  登录页据此决定是否渲染滑块:pass/lock 不加载阿里云滑块 SDK(连前端请求都不发)。 */
    @Operation(summary = "当前登录滑块验证模式(登录页探活,免登录)")
    @GetMapping("/captcha-mode")
    public R<Map<String, String>> captchaMode() {
        return R.ok(Map.of("mode", systemConfigService.getCaptchaMode().getCode()));
    }

    /** 知识库问答开关(免登录探活):悬浮精灵据此决定展示正常问答还是"已被管理员禁用"占位,避免无效 /ask 调用。 */
    @Operation(summary = "知识库问答是否开启(免登录)")
    @GetMapping("/kb-enabled")
    public R<Map<String, Boolean>> kbEnabled() {
        return R.ok(Map.of("enabled", systemConfigService.isKbEnabled()));
    }

    @OperationLog(module = "认证", operation = "登出")
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public R<Void> logout() {
        loginService.logout();
        return R.ok();
    }

    private String clientIp(HttpServletRequest r) {
        String x = r.getHeader("X-Forwarded-For");
        return (x == null || x.isEmpty()) ? r.getRemoteAddr() : x.split(",")[0].trim();
    }
}
