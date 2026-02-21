package com.trendscope.backend.domain.analyze.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
public class AnalyzeShareTokenService {

    private static final String SHARE_TOKEN_TYPE = "analyze_share";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.analyze.share-token-expire-hours:72}")
    private long shareTokenExpireHours;

    private SecretKey secretKey;

    @PostConstruct
    public void initialize() {
        this.secretKey = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                Jwts.SIG.HS256.key().build().getAlgorithm()
        );
    }

    public IssuedShareToken issueToken(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("공유 토큰 생성 대상 jobId가 비어 있습니다.");
        }

        long validHours = Math.max(1L, shareTokenExpireHours);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(validHours * 3600L);

        String token = Jwts.builder()
                .subject(jobId)
                .claim("type", SHARE_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new IssuedShareToken(token, LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public String extractJobId(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("공유 토큰이 비어 있습니다.");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get("type", String.class);
            if (!SHARE_TOKEN_TYPE.equals(type)) {
                throw new IllegalArgumentException("유효하지 않은 공유 토큰 타입입니다.");
            }

            String jobId = claims.getSubject();
            if (jobId == null || jobId.isBlank()) {
                throw new IllegalArgumentException("유효하지 않은 공유 토큰입니다.");
            }
            return jobId;
        } catch (JwtException e) {
            throw new IllegalArgumentException("공유 링크가 만료되었거나 유효하지 않습니다.");
        }
    }

    public record IssuedShareToken(
            String token,
            LocalDateTime expiresAt
    ) {
    }
}
