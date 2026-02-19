package com.trendscope.backend.global.config;

import com.trendscope.backend.global.util.JWTUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenExpirationMs;

    @PostConstruct
    public void init() {
        JWTUtil.initialize(secret, accessTokenExpirationMs, refreshTokenExpirationMs);
    }
}
