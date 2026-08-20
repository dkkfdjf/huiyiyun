package com.huiyi.modules.auth;

import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.JwtUtil;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.system.CaptchaMode;
import com.huiyi.modules.system.SystemConfigService;
import com.huiyi.modules.system.mapper.DoctorMapper;
import com.huiyi.modules.system.mapper.LoginLogMapper;
import com.huiyi.modules.system.mapper.PharmaCompanyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 登录测试。登录已不再校验图形验证码(改为前端滑块门禁 + 失败锁定),
 * 故本类不再 mock CaptchaService;doctor 档案查询随 M8 补入 LoginService,一并 mock。
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock UserMapper userMapper;
    @Mock LoginLogMapper loginLogMapper;
    @Mock PharmaCompanyMapper companyMapper;
    @Mock DoctorMapper doctorMapper;
    @Mock SystemConfigService systemConfigService;
    @Spy BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @InjectMocks LoginService service;

    private final JwtUtil jwt = new JwtUtil("test-secret-at-least-32-characters-long-xxxx", 30);

    @BeforeEach
    void wire() {
        service.setJwtUtil(jwt);
        // captcha mode(LOCK/ENABLED/PASS 三态,防阿里云费用超限)功能加入时漏的 mock:
        // 模拟 PASS(关闭滑块放行),login() 跳过 captcha 校验,聚焦测登录主流程
        when(systemConfigService.getCaptchaMode()).thenReturn(CaptchaMode.PASS);
    }

    @Test
    void success_returns_token() {
        User u = user("admin", encoder.encode("admin123"), 0, 1, 0);
        when(userMapper.selectOne(any())).thenReturn(u);

        LoginVO vo = service.login(dto("admin", "admin123"), "1.1.1.1", "ua");

        assertNotNull(vo.getToken());
        assertEquals(0, vo.getRole());
        assertEquals("系统管理员", vo.getRealName());
    }

    @Test
    void wrong_password_increments_fail_and_throws() {
        User u = user("admin", encoder.encode("correct"), 0, 1, 0);
        when(userMapper.selectOne(any())).thenReturn(u);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.login(dto("admin", "wrong"), "1.1.1.1", "ua"));
        // 凭据错误抛 BAD_CREDENTIALS(4007) 非 UNAUTHORIZED(401):401 在前端=会话过期→跳登录,
        // 登录页密码错应留在登录页,故区分(见 LoginService 注释)。旧测试期望 401,业务已改 4007,此处同步。
        assertEquals(ResultCode.BAD_CREDENTIALS.getCode(), e.getCode());
        assertEquals(1, u.getLoginFailCount());
        // 显式定型为 User:BaseMapper 同时有 updateById(T) 与 updateById(Collection<T>) 批量重载,裸 any() 歧义致编译失败
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    void third_failure_locks_account() {
        User u = user("admin", encoder.encode("correct"), 0, 1, 2); // 已失败 2 次
        when(userMapper.selectOne(any())).thenReturn(u);

        assertThrows(BusinessException.class,
                () -> service.login(dto("admin", "wrong"), "1.1.1.1", "ua"));
        assertEquals(3, u.getLoginFailCount());
        assertNotNull(u.getLockedUntil());
    }

    /** 轻量加固:同一 IP 在窗口内失败达阈值 → 直接拒绝(防撞库),不到查用户/比密码。 */
    @Test
    void ip_throttle_blocks_after_many_fails() {
        when(loginLogMapper.countRecentFailsByIp(any(), any())).thenReturn(10L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.login(dto("admin", "admin123"), "1.1.1.1", "ua"));
        assertEquals(ResultCode.LOGIN_TOO_MANY.getCode(), e.getCode());
        verify(userMapper, never()).selectOne(any());   // 早拒,未查用户
    }

    @Test
    void locked_account_throws_locked() {
        User u = user("admin", encoder.encode("admin123"), 0, 1, 5);
        u.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(20));
        when(userMapper.selectOne(any())).thenReturn(u);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.login(dto("admin", "admin123"), "1.1.1.1", "ua"));
        assertEquals(ResultCode.ACCOUNT_LOCKED.getCode(), e.getCode());
    }

    @Test
    void disabled_account_throws() {
        User u = user("admin", encoder.encode("admin123"), 0, 0, 0); // status=0
        when(userMapper.selectOne(any())).thenReturn(u);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.login(dto("admin", "admin123"), "1.1.1.1", "ua"));
        assertEquals(ResultCode.ACCOUNT_DISABLED.getCode(), e.getCode());
    }

    // ---- helpers ----
    private LoginDTO dto(String name, String pwd) {
        LoginDTO d = new LoginDTO();
        d.setUsername(name);
        d.setPassword(pwd);
        return d;
    }

    private User user(String name, String pwdHash, int role, int status, int fails) {
        User u = new User();
        u.setId(1L);
        u.setUsername(name);
        u.setPassword(pwdHash);
        u.setRealName("系统管理员");
        u.setRole(role);
        u.setStatus(status);
        u.setLoginFailCount(fails);
        return u;
    }
}
