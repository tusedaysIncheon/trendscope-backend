package com.trendscope.backend.global.jwt.api;

import com.trendscope.backend.global.jwt.model.JWTResponseDTO;
import com.trendscope.backend.global.jwt.model.RefreshRequestDTO;
import com.trendscope.backend.global.jwt.service.JwtService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "JWT API", description = "Refresh Cookie 기반 Access 토큰 발급/갱신 API")
public class JWTController {

    private final JwtService jwtService;

    // 소셜 로그인 방식: 쿠키 -> JWT 교환
    @Operation(summary = "JWT 교환", description = "refresh_token 쿠키를 Access 토큰으로 교환하고 Refresh 토큰을 회전합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "교환 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "쿠키/토큰 검증 실패"),
    })
    @PostMapping(value = "/jwt/exchange")
    public ApiResponse<JWTResponseDTO> jwtExchangeAPI(
            HttpServletResponse response,
            HttpServletRequest request,
            // Body가 없어도 에러나지 않게 required = false 설정
            @RequestBody(required = false) Map<String, String> body) {
        String deviceId = "unknown-device-id"; // 기본값 설정

        // Body가 있고, 그 안에 deviceId가 있을 때만 덮어쓰기
        if (body != null && body.get("deviceId") != null) {
            deviceId = body.get("deviceId");
        }

        return ApiResponse.ok(jwtService.cookie2token(response, request, deviceId));
    }

    // Refresh 토큰으로 Access 토큰 재발급
    @Operation(summary = "JWT 갱신", description = "refresh_token 쿠키로 Access 토큰을 재발급합니다. 쿠키가 없으면 게스트 모드 응답을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공/게스트 모드"),
    })
    @PostMapping(value = "/jwt/refresh")
    public ApiResponse<?> jwtRefreshAPI(
            HttpServletRequest request,
            HttpServletResponse response,
            // Body가 비어있어도 괜찮도록 required = false
            @RequestBody(required = false) Map<String, String> body) {
        String refreshToken = null;

        // 1. 쿠키에서 리프레시 토큰 찾기
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                // 이름이 "refresh_token" 또는 "refreshToken" 인지 확인
                if ("refresh_token".equals(cookie.getName()) || "refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        // 2. 쿠키 값 검증 (없으면 게스트)
        if (refreshToken == null || refreshToken.isBlank()) {

            // Guest Mode
            // Assuming returning a Map equivalent in ApiResponse logic or just success with
            // data
            return ApiResponse.ok(Map.of("message", "Guest Mode", "accessToken", ""));
        }

        // 3. deviceId 처리 (Null Safe 하게 변경)
        String deviceId = "unknown-device-id"; // 기본값
        if (body != null && body.get("deviceId") != null) {
            deviceId = body.get("deviceId");
        }
        // 예외 던지는 코드(throw new ...) 삭제함!

        // 4. 서비스 호출을 위한 DTO 생성
        RefreshRequestDTO dto = new RefreshRequestDTO();
        dto.setRefreshToken(refreshToken);

        // 5. 토큰 재발급 진행
        try {
            JWTResponseDTO newToken = jwtService.refreshRotate(dto, response, deviceId);
            return ApiResponse.ok(newToken);
        } catch (Exception e) {
            return ApiResponse.fail("400", "리프레쉬 토큰 확인안됨");
        }

    }
}
