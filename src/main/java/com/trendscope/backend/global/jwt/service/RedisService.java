package com.trendscope.backend.global.jwt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 리프레시 토큰 저장 (Save) - Hash 구조 변경
     * Key: RT:username
     * Field: deviceId
     * Value: refreshToken
     * 
     * @param username         사용자 아이디
     * @param deviceId         기기 식별자 (UUID)
     * @param refreshToken     저장할 토큰 값
     * @param durationInMillis 토큰 유효 시간 (밀리초) - Key 만료 시간으로 사용
     */
    public void setRefreshToken(String username, String deviceId, String refreshToken, Long durationInMillis) {
        String key = "RT:" + username;

        // Hash에 저장 (Key, Field, Value)
        redisTemplate.opsForHash().put(key, deviceId, refreshToken);

        // Key 전체에 대한 만료 시간 설정 (갱신)
        // 사용자가 활동할 때마다 세션 만료 시간이 연장됨
        redisTemplate.expire(key, Duration.ofMillis(durationInMillis));
    }

    /**
     * 리프레시 토큰 조회 (Get)
     * 
     * @return 저장된 토큰 값 (없으면 null)
     */
    public String getRefreshToken(String username, String deviceId) {
        String key = "RT:" + username;

        // Hash에서 Field(deviceId)로 조회
        return (String) redisTemplate.opsForHash().get(key, deviceId);
    }

    /**
     * 리프레시 토큰 삭제 (Delete)
     * 로그아웃 시 사용
     */
    public void deleteRefreshToken(String username, String deviceId) {
        String key = "RT:" + username;

        // Hash에서 특정 Field(deviceId)만 삭제
        redisTemplate.opsForHash().delete(key, deviceId);
    }

    /**
     * 해당 유저의 모든 리프레시 토큰 삭제
     * 비밀번호 변경, 회원 탈퇴, 전체 로그아웃 등
     */
    public void deleteAllRefreshTokens(String username) {
        String key = "RT:" + username;
        // Hash Key 자체를 삭제하면 연결된 모든 Field(기기) 정보가 삭제됨
        redisTemplate.delete(key);
    }

    public void setStringValue(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public String getStringValue(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value == null ? null : value.toString();
    }

    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    public Long increment(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count;
    }

}
