package com.huiyi.common.security;

import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class RequiresRoleAspect {

    @Before("@annotation(rr)")
    public void check(RequiresRole rr) {
        CurrentUser u = SecurityContextHolder.get();
        if (u == null) {
            log.warn("RequiresRoleAspect - CurrentUser is null, throwing UNAUTHORIZED");
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (Arrays.stream(rr.value()).noneMatch(r -> r == u.getRole())) {
            log.warn("RequiresRoleAspect - Role mismatch: userRole={}, required={}, throwing FORBIDDEN",
                    u.getRole(), Arrays.toString(rr.value()));
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
