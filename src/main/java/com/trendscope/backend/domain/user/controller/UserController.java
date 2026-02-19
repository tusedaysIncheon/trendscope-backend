package com.trendscope.backend.domain.user.controller;

import com.trendscope.backend.domain.user.dto.*;
import com.trendscope.backend.global.jwt.service.RedisService;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.service.UserService;
import com.trendscope.backend.global.exception.FeatureDisabledException;
import com.trendscope.backend.global.util.ApiResponse;
import com.trendscope.backend.global.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
// import org.springframework.security.core.annotation.AuthenticationPrincipal; // Removed
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/user")
@Tag(name = "User API", description = "소셜 로그인 기반 유저 정보 관리 API")
public class UserController {

    private final UserService userService;
    private final RedisService redisService;

    @Operation(summary = "회원가입 여부 확인", description = "자체 로그인 관련 회원가입 진행 시 해당 유저가 존재 하는지 확인 API.")
    @PostMapping(value = "/exist", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Boolean> exist(
            @Validated(UserRequestDTO.existGroup.class) @RequestBody UserRequestDTO dto) {
        throw new FeatureDisabledException("자체 회원가입 기능은 비활성화되었습니다.");
    }

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록 API.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UserResponseDTO> registerUserApi(
            @Validated(UserRequestDTO.addGroup.class) @RequestBody UserRequestDTO dto) {
        throw new FeatureDisabledException("자체 회원가입 기능은 비활성화되었습니다.");
    }

    @Operation(summary = "내 정보 조회", description = "유저정보 불러오기 API.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UserResponseDTO> userMeApi() {
        return ApiResponse.ok(userService.readUser());
    }

    @Operation(summary = "내 정보 수정", description = "회원 정보 수정 API.")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Long> updateUserApi(
            @Validated(UserRequestDTO.updateGroup.class) @RequestBody UserRequestDTO dto) throws AccessDeniedException {
        return ApiResponse.ok(userService.updateUser(dto));
    }

    @Operation(summary = "회원탈퇴", description = "회원탈퇴 (JWT RefreshToken remove 및 회원정보 db 삭제) API.")
    @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Boolean> deleteUserApi(
            @Validated(UserRequestDTO.deleteGroup.class) @RequestBody UserRequestDTO dto) throws AccessDeniedException {
        userService.deleteUser(dto);
        return ApiResponse.ok(true);
    }

    @Operation(summary = "로그인", description = "로그인 API (SameSite 쿠키 적용)")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AuthLoginResponseDTO> loginApi(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletResponse response) {
        throw new FeatureDisabledException("자체 로그인 기능은 비활성화되었습니다. 소셜 로그인만 지원합니다.");
    }

    @Operation(summary = "로그아웃", description = "로그아웃 API")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
    })
    @PostMapping("/logout")
    public ApiResponse<Boolean> logoutApi(
            HttpServletResponse response,
            // @AuthenticationPrincipal CustomUserDetails user, // Removed
            @RequestBody(required = false) Map<String, String> body) {

        UserEntity user = SecurityUtils.getCurrentUser();

        String deviceId = (body != null && body.get("deviceId") != null) ? body.get("deviceId") : "unknown-device-id";

        // 1. Redis 삭제
        if (user != null) {
            String username = user.getUsername();
            redisService.deleteRefreshToken(username, deviceId);
        }

        // ★ 수정 포인트: 쿠키 만료 처리도 ResponseCookie 사용
        response.addHeader("Set-Cookie", "refresh_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");

        return ApiResponse.ok(true);
    }


}
