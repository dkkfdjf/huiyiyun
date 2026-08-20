package com.huiyi.common.security;

import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
public class JwtUtil {

    private final SecretKey key;
    private final long expireMs;
    private final long guestExpireMs;   // 游客临时令牌有效期(远短于正式 token,缩泄露窗口)

    /** 二元构造:游客期限默认等同正式(测试/兼容旧调用)。 */
    public JwtUtil(String secret, int expireMinutes) {
        this(secret, expireMinutes, expireMinutes);
    }

    /** 三元构造:正式 token 用 expireMinutes,游客临时令牌用 guestExpireMinutes(生产由 SecurityConfig 注入)。 */
    public JwtUtil(String secret, int expireMinutes, int guestExpireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireMinutes * 60_000L;
        this.guestExpireMs = guestExpireMinutes * 60_000L;
    }

    /** Token 有效期(分钟)——与签发时实际使用的过期同源,供 LoginService 回填 expireAt,避免硬编码漂移。 */
    public long getExpireMinutes() {
        return expireMs / 60_000L;
    }

    /** 游客临时令牌有效期(分钟)——供 LoginService 回填游客 expireAt,与 generateGuest 同源。 */
    public long getGuestExpireMinutes() {
        return guestExpireMs / 60_000L;
    }

    /** 签发游客临时令牌:claim 同 generate,仅过期用 guestExpireMs(短期,默认 2h)。 */
    public String generateGuest(CurrentUser u) {
        Date now = new Date();
        return Jwts.builder()
                .claim("userId", u.getUserId())
                .claim("username", u.getUsername())
                .claim("role", u.getRole())
                .claim("companyId", u.getCompanyId())
                .claim("institutionId", u.getInstitutionId())
                .claim("doctorId", u.getDoctorId())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + guestExpireMs))
                .signWith(key)
                .compact();
    }

    public String generate(CurrentUser u) {
        Date now = new Date();
        return Jwts.builder()
                .claim("userId", u.getUserId())
                .claim("username", u.getUsername())
                .claim("role", u.getRole())
                .claim("companyId", u.getCompanyId())
                .claim("institutionId", u.getInstitutionId())
                .claim("doctorId", u.getDoctorId())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    public CurrentUser parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            CurrentUser u = new CurrentUser();
            u.setUserId(longVal(c, "userId"));
            u.setUsername(c.get("username", String.class));
            u.setRole(intVal(c, "role"));
            u.setCompanyId(c.get("companyId") == null ? null : longVal(c, "companyId"));
            u.setInstitutionId(c.get("institutionId") == null ? null : longVal(c, "institutionId"));
            u.setDoctorId(c.get("doctorId") == null ? null : longVal(c, "doctorId"));
            return u;
        } catch (Exception e) {
            log.debug("jwt parse failed: {}", e.getMessage());
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
    }

    private static Long longVal(Claims c, String k) {
        Object v = c.get(k);
        return v == null ? null : ((Number) v).longValue();
    }

    private static Integer intVal(Claims c, String k) {
        Object v = c.get(k);
        return v == null ? null : ((Number) v).intValue();
    }
}
