package com.huiyi.common.web;

import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.common.security.view.GuestView;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 游客脱敏统一收口:role==GUEST 且响应为 JSON 时,把 body 包成 {@link MappingJacksonValue}
 * 并激活 {@link GuestView} 序列化 → 自动排除实体上 @JsonView(InternalView.class) 标注的敏感字段。
 * <p>非游客角色原样放行(不激活任何视图=全字段,既有行为不变)。
 * <p>这是游客"不能有敏感数据"的最后一道序列化层防线;前道是后端 @RequiresRole 不放写端点 + 前端只读化。
 */
@RestControllerAdvice
public class GuestViewResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        CurrentUser cu = SecurityContextHolder.get();
        if (cu == null || cu.getRole() == null || cu.getRole() != RoleConstants.GUEST) {
            return body;
        }
        // 认证端点(/auth/**:登录、游客签令牌、验证码、探活)的响应是"发令牌/探活"本身,
        // 绝不能套 GuestView——LoginVO 等无 @JsonView 字段一旦被视图过滤会丢 token/role,
        // 游客将拿不到令牌或角色错乱。脱敏只针对业务数据端点。
        String path = request.getURI().getPath();
        if (path != null && path.startsWith("/api/v1/auth/")) {
            return body;
        }
        // 仅 JSON 响应才套视图(游客不会命中非 JSON 端点,此判断纯防御)。
        if (selectedContentType == null || !selectedContentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return body;
        }
        if (body instanceof MappingJacksonValue) {
            ((MappingJacksonValue) body).setSerializationView(GuestView.class);
            return body;
        }
        MappingJacksonValue wrapped = new MappingJacksonValue(body);
        wrapped.setSerializationView(GuestView.class);
        return wrapped;
    }
}
