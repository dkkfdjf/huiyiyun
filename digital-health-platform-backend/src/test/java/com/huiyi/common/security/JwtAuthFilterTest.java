package com.huiyi.common.security;

import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 鉴权过滤器测试。重点复现「禁用账号的旧 token 操作窗口」高危缺口:
 * token 本身合法,但账号在 DB 中已被停用(status=0),过滤器须当场拦截,
 * 不得放行给下游服务(否则旧 token 在 30 分钟有效期内仍可操作)。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtUtil jwtUtil;
    @Mock UserMapper userMapper;
    @Mock HttpServletRequest req;
    @Mock HttpServletResponse resp;
    @Mock FilterChain chain;
    @InjectMocks JwtAuthFilter filter;

    /** 高危缺口 #1:合法 token + 账号已禁用 → 必须拒,不得放行。 */
    @Test
    void disabled_account_token_is_rejected() throws Exception {
        CurrentUser cu = companyUser(7L, 2L);
        when(req.getRequestURI()).thenReturn("/api/v1/inventory/sales");
        when(req.getHeader("Authorization")).thenReturn("Bearer t.ok");
        when(jwtUtil.parse("t.ok")).thenReturn(cu);
        User disabled = new User();
        disabled.setId(7L);
        disabled.setStatus(0); // 药企被停用 → 账号同步禁用
        when(userMapper.selectById(7L)).thenReturn(disabled);
        when(resp.getWriter()).thenReturn(mock(PrintWriter.class));

        filter.doFilterInternal(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_OK);
        verify(resp).getWriter();          // 写了 401 响应体
        verify(chain, never()).doFilter(any(), any());
    }

    /** 对照:正常账号的合法 token 放行。 */
    @Test
    void active_account_token_proceeds() throws Exception {
        CurrentUser cu = companyUser(7L, 2L);
        when(req.getRequestURI()).thenReturn("/api/v1/inventory/sales");
        when(req.getHeader("Authorization")).thenReturn("Bearer t.ok");
        when(jwtUtil.parse("t.ok")).thenReturn(cu);
        User active = new User();
        active.setId(7L);
        active.setStatus(1);
        when(userMapper.selectById(7L)).thenReturn(active);

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    /** 账号被物理删除(selectById=null)同样视为不可用。 */
    @Test
    void deleted_account_token_is_rejected() throws Exception {
        CurrentUser cu = companyUser(99L, 2L);
        when(req.getRequestURI()).thenReturn("/api/v1/inventory/sales");
        when(req.getHeader("Authorization")).thenReturn("Bearer t.ok");
        when(jwtUtil.parse("t.ok")).thenReturn(cu);
        when(userMapper.selectById(99L)).thenReturn(null);
        when(resp.getWriter()).thenReturn(mock(PrintWriter.class));

        filter.doFilterInternal(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
    }

    private CurrentUser companyUser(Long userId, Long companyId) {
        CurrentUser cu = new CurrentUser();
        cu.setUserId(userId);
        cu.setUsername("pharma");
        cu.setRole(RoleConstants.COMPANY);
        cu.setCompanyId(companyId);
        return cu;
    }
}
