package com.huiyi.common.security;

import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Authorization: Bearer {jwt} → 写入 ThreadLocal CurrentUser。
 * 白名单(登录/文档)放行;受保护 /api/ 接口缺 token 或 token 非法 → 401。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/guest",          // 游客体验入口(免登录,签发 GUEST 临时令牌)
            "/api/v1/auth/guest-enabled",  // 登录页探活:游客入口是否开放(免登录,决定是否渲染游客按钮)
            "/api/v1/auth/captcha",
            "/api/v1/auth/captcha-mode",   // 登录页探活:当前滑块验证模式(免登录,返回 enabled/pass/lock)
            "/api/v1/auth/kb-enabled",     // 悬浮精灵探活:知识库问答是否开启(免登录,决定是否渲染禁用态)
            "/api/v1/public",            // 首页落地页公开概览(免登录,仅聚合脱敏数据)
            "/doc.html", "/swagger", "/swagger-ui", "/v3/api-docs", "/webjars", "/favicon"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        try {
            if (WHITELIST.stream().anyMatch(uri::startsWith)) {
                chain.doFilter(req, resp);
                return;
            }
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                try {
                    CurrentUser cu = jwtUtil.parse(auth.substring(7));
                    // 游客是临时令牌、无真实 user 行:不复查 status(无可禁用账号,复查无安全增益),
                    // 直接放行。其余角色保留防御层:token 合法 ≠ 账号仍可用。
                    if (cu.getRole() != null && cu.getRole() == RoleConstants.GUEST) {
                        SecurityContextHolder.set(cu);
                    } else {
                        // 按 userId 复查 user.status,账号被禁用/删除(含药企停用时联动置 status=0)当场拦截,
                        // 堵住「旧 token 在 30 分钟有效期内仍可操作」的高危窗口。
                        User u = userMapper.selectById(cu.getUserId());
                        if (u == null || u.getStatus() == null || u.getStatus() != 1) {
                            log.warn("JwtAuthFilter - User not available: userId={}", cu.getUserId());
                            writeUnauthorized(resp);
                            return;
                        }
                        SecurityContextHolder.set(cu);
                    }
                } catch (Exception e) {
                    log.error("JwtAuthFilter - JWT parse failed: {}", e.getMessage());
                    // token 过期/非法:Filter 内抛出的异常不会被 @RestControllerAdvice 捕获(它只拦 Controller),
                    // 不兜住会冒泡成 500。此处写干净 {code:401},前端 unwrap() 见 401 自动清 token 跳登录。
                    writeUnauthorized(resp);
                    return;
                }
            } else if (uri.startsWith("/api/")) {
                log.warn("JwtAuthFilter - No Authorization header for API request, returning 401");
                writeUnauthorized(resp);
                return;
            }
            chain.doFilter(req, resp);
        } finally {
            SecurityContextHolder.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":401,\"message\":\"未授权或登录已过期\",\"data\":null}");
    }
}
