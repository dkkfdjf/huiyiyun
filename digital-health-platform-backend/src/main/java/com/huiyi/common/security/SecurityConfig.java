package com.huiyi.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${huiyi.jwt.secret}") String secret,
                           @Value("${huiyi.jwt.expire-minutes}") int expireMinutes,
                           @Value("${huiyi.jwt.guest-expire-minutes:120}") int guestExpireMinutes) {
        return new JwtUtil(secret, expireMinutes, guestExpireMinutes);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
