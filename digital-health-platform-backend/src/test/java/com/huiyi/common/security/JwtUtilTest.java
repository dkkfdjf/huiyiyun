package com.huiyi.common.security;

import com.huiyi.common.result.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwt = new JwtUtil("test-secret-at-least-32-characters-long-xxxx", 30);

    @Test
    void roundtrip_preserves_claims() {
        CurrentUser u = new CurrentUser();
        u.setUserId(1L);
        u.setUsername("admin");
        u.setRole(0);
        u.setCompanyId(10L);

        String token = jwt.generate(u);
        CurrentUser parsed = jwt.parse(token);

        assertEquals(1L, parsed.getUserId());
        assertEquals("admin", parsed.getUsername());
        assertEquals(0, parsed.getRole());
        assertEquals(10L, parsed.getCompanyId());
    }

    @Test
    void null_optionals_stay_null() {
        CurrentUser u = new CurrentUser();
        u.setUserId(2L);
        u.setUsername("doctor01");
        u.setRole(3);
        u.setInstitutionId(5L);
        // companyId / doctorId 留空

        CurrentUser parsed = jwt.parse(jwt.generate(u));
        assertNull(parsed.getCompanyId());
        assertEquals(5L, parsed.getInstitutionId());
    }

    @Test
    void invalid_token_throws_unauthorized() {
        assertThrows(BusinessException.class, () -> jwt.parse("not-a-jwt"));
    }
}
