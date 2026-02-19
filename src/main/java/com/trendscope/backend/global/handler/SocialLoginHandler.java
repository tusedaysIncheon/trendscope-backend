package com.trendscope.backend.global.handler;

import com.trendscope.backend.global.jwt.service.JwtService;
import com.trendscope.backend.global.util.JWTUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Qualifier("socialSuccessHandler")
@RequiredArgsConstructor
public class SocialLoginHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {

        // username , role 파싱
        String username = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        String deviceId = "unknown-device-id";

        // JWT 발급
        String refreshToken = JWTUtil.createJWT(username, "ROLE_" + role, false);

        // 발급한 리프레쉬 토큰 DB 테이블 저장
        jwtService.addRefresh(username, refreshToken, deviceId);

        // 응답
        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(60);

        response.addCookie(refreshCookie);
        response.sendRedirect(frontendBaseUrl + "/cookie");

    }
}
