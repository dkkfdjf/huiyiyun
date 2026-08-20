package com.huiyi.modules.auth;

import com.huiyi.common.exception.GlobalExceptionHandler;
import com.huiyi.common.result.ResultCode;
import com.huiyi.modules.log.ErrorLogService;
import com.huiyi.modules.system.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证控制器测试。用 standalone MockMvc(不加载 Spring 上下文),避免 @WebMvcTest
 * 拿主类装配上下文时触发 MyBatis @MapperScan / DataSource / Security 等切片无关 bean
 * 导致 ApplicationContext 加载失败。手动装配 AuthController + GlobalExceptionHandler。
 * 覆盖登录成功与参数校验(@Valid)。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock LoginService loginService;
    @Mock CaptchaService captchaService;
    @Mock SystemConfigService systemConfigService;
    @Mock ErrorLogService errorLogService;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        AuthController controller = new AuthController(loginService, captchaService, systemConfigService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(errorLogService))
                .build();
    }

    @Test
    void login_returns_token() throws Exception {
        LoginVO vo = new LoginVO();
        vo.setToken("t");
        vo.setRole(0);
        when(loginService.login(any(), any(), any())).thenReturn(vo);

        String body = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("t"))
                .andExpect(jsonPath("$.data.role").value(0));
    }

    @Test
    void blank_fields_yield_param_invalid() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_INVALID.getCode()));
    }
}
