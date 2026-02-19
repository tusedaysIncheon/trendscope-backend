package com.trendscope.backend.global.jwt.service;

import com.trendscope.backend.global.exception.InvalidTokenException;
import com.trendscope.backend.global.jwt.model.JWTResponseDTO;
import com.trendscope.backend.global.jwt.model.RefreshRequestDTO;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import com.trendscope.backend.global.util.JWTUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final UserRepository userRepository;
    private final RedisService redisService;

    @Value("${JWT_REFRESH_EXPIRATION_MS}")
    private Long refreshTokenExpirationMs;

    // 소셜 로그인 성공 후 쿠키(Refresh) -> 헤더 방식으로 응답
    @Transactional
    public JWTResponseDTO cookie2token(HttpServletResponse response, HttpServletRequest request, String deviceId) {

        // 쿠키가 있는지 검증
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new InvalidTokenException("쿠키가 존재하지 않습니다.");
        }

        // 자바에서 리터럴 비교 시 문자열이 앞으로 오게해서 비교해야함 -> NPE 방지
        for (Cookie cookie : cookies) {
            if ("refresh_token".equals(cookie.getName()) || "refreshToken".equals(cookie.getName())) {
                refreshToken = cookie.getValue();
                break;
            }
        }

        if (refreshToken == null) {
            throw new InvalidTokenException("리프레쉬 토큰이 확인되지 않습니다.");
        }

        // refresh 토큰 검증
        Boolean isValid = JWTUtil.isValid(refreshToken, false);
        if (!isValid) {
            throw new InvalidTokenException("유효하지 않은 리프레쉬 토큰입니다.");
        }

        String username = JWTUtil.getUsername(refreshToken);
        String role = JWTUtil.getRole(refreshToken);

        // Redis 조회 및 검증
        // DB 시절: findByRefresh(token) -> deviceId 알게 됨
        // Redis 시절: username + deviceId로 Key를 만들어서 조회 -> 저장된 토큰과 비교

        String savedToken = redisService.getRefreshToken(username, deviceId);

        if (savedToken == null) {
            savedToken = redisService.getRefreshToken(username, "unknown-device-id");
        }

        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new InvalidTokenException("유효하지 않거나 만료된 토큰입니다.");
        }

        String newAccessToken = createAccessToken(username);
        String newRefreshToken = createRefreshToken(username); // Rotation Enable

        redisService.setRefreshToken(username, deviceId, newRefreshToken, refreshTokenExpirationMs);

        if (!"unknown-device-id".equals(deviceId)) {
            redisService.deleteRefreshToken(username, "unknown-device-id");
        }

        setRefreshCookie(response, newRefreshToken);

        Cookie refreshCookie = new Cookie("refresh_token", newRefreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(refreshCookie);

        return new JWTResponseDTO(newAccessToken);

    }

    // Refresh 토큰으로 Access 토큰 재발급 로직 (Rotate 포함)
    @Transactional
    public JWTResponseDTO refreshRotate(RefreshRequestDTO dto, HttpServletResponse response, String deviceId) {

        String refreshToken = dto.getRefreshToken();

        if (!JWTUtil.isValid(refreshToken, false)) {
            throw new InvalidTokenException("유효하지 않은 리프레쉬토큰입니다.");
        }

        String username = JWTUtil.getUsername(refreshToken);
        String role = JWTUtil.getRole(refreshToken);

        // redis 검증
        String savedToken = redisService.getRefreshToken(username, deviceId);

        if (savedToken == null) {
            throw new InvalidTokenException("만료된 세션입니다. 다시 로그인해주세요");
        }

        if (!savedToken.equals(refreshToken)) {
            // 토큰 정보 불일치 (보안 경고)
            // refreshToken 삭제 / 강제 로그아웃
            throw new InvalidTokenException("토큰 정보가 일치하지않습니다");
        }

        String newAccessToken = createAccessToken(username);
        String newRefreshToken = createRefreshToken(username);

        redisService.setRefreshToken(username, deviceId, newRefreshToken, refreshTokenExpirationMs);

        setRefreshCookie(response, newRefreshToken);

        return new JWTResponseDTO(newAccessToken);
    }

    // JWT Refresh 토큰 발급 후 저장 메소드
    @Transactional
    public void addRefresh(String username, String refreshToken, String deviceId) {

        // Redis 는 set 하면 덮어써짐 // -> 삭제 로직 불필요
        redisService.setRefreshToken(username, deviceId, refreshToken, refreshTokenExpirationMs);

    }

    // 로그인 시 access 토큰 발급 메소드
    public String createAccessToken(String username) {

        String role = userRepository.findRoleTypeByUsername(username)
                .map(Enum::name)
                .orElse(UserRoleType.USER.name());

        return JWTUtil.createJWT(username, role, true);
    }

    // 로그인 시 refresh 토큰 발급 메소드
    public String createRefreshToken(String username) {
        String role = userRepository.findRoleTypeByUsername(username)
                .map(Enum::name)
                .orElse(UserRoleType.USER.name());

        return JWTUtil.createJWT(username, role, false);
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge((int) (refreshTokenExpirationMs / 1000));
        response.addCookie(refreshCookie);
    }

}
