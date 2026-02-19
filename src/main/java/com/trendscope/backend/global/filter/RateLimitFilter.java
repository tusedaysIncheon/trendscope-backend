package com.trendscope.backend.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final LettuceBasedProxyManager<byte[]> proxyManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    )
            throws ServletException, IOException {

        String clientIp = getClientIp(request);
        String requestUri = request.getRequestURI();

        String blockKey = "BLOCK:" + clientIp;
        if(Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))){
            sendErrorResponse(response,"한번에 너무 많은 요청을 하여 30분간 이용이 제한되었습니다.", HttpStatus.TOO_MANY_REQUESTS);
            return;
        }

        //버킷 설정값
        long capacity;
        if(requestUri.contains("/v1/user/login")){
            capacity = 20;
        } else {
            capacity = 100;
        }

        //버켓 키 설정
        String bucketKey = "BUCKET:" + clientIp + ":" + (capacity == 20 ? "login" : "general");
        byte[] keyBytes = bucketKey.getBytes(StandardCharsets.UTF_8);

        Bucket bucket = proxyManager.builder().build(keyBytes, ()-> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(1))
                        .build())
                .build());

        //티켓검증
        if(bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            //토큰 부족
            log.warn("트래픽초과 {} 를 차단합니다.", clientIp);
            redisTemplate.opsForValue().set(blockKey,"BLOCKED",30, TimeUnit.MINUTES);
            sendErrorResponse(response, "너무 많은 요청으로 30분간 제한됩니다.", HttpStatus.TOO_MANY_REQUESTS);
        }

    }



    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        if(ip != null && ip.contains(",")){
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private void sendErrorResponse(HttpServletResponse response, String message, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("code", "TOO_MANY_REQUESTS");
        errorMap.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(errorMap));
    }
}
