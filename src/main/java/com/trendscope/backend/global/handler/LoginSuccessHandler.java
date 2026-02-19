package com.trendscope.backend.global.handler;

import com.trendscope.backend.global.jwt.service.JwtService;
import com.trendscope.backend.global.util.JWTUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Qualifier("LoginSuccessHandler")
public class LoginSuccessHandler implements AuthenticationSuccessHandler {


    private final JwtService jwtService;

    public LoginSuccessHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

            // username, role
            String username =  authentication.getName();
            String role = authentication.getAuthorities().iterator().next().getAuthority();

            // JWT(Access/Refresh) 발급
            String accessToken = JWTUtil.createJWT(username, role, true);
            String refreshToken = JWTUtil.createJWT(username, role, false);

            String deviceId = request.getHeader("Device-Id");
            if (deviceId == null || deviceId.isBlank()) {
                deviceId = request.getHeader("User-Agent"); // fallback
            }

            // 발급한 Refresh DB 테이블 저장 (Refresh whitelist)
            jwtService.addRefresh(username, refreshToken, deviceId);

            Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
            refreshCookie.setHttpOnly(true);
            refreshCookie.setSecure(false); // 프로덕션은 true
            refreshCookie.setPath("/");
            refreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7일
            response.addCookie(refreshCookie);

            // 응답
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            String json = String.format("{\"accessToken\": \"%s\"}", accessToken);
            response.getWriter().write(json);
            response.getWriter().flush();
        }

}