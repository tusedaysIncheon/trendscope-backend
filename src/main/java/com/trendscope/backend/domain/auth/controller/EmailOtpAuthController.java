package com.trendscope.backend.domain.auth.controller;

import com.trendscope.backend.domain.auth.dto.EmailOtpRequestDTO;
import com.trendscope.backend.domain.auth.dto.EmailOtpVerifyRequestDTO;
import com.trendscope.backend.domain.auth.service.EmailOtpAuthService;
import com.trendscope.backend.domain.user.dto.AuthLoginResponseDTO;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth/email-otp")
@Tag(name = "Email OTP Auth API", description = "이메일 OTP 기반 로그인 API")
public class EmailOtpAuthController {

    private final EmailOtpAuthService emailOtpAuthService;

    @Operation(summary = "OTP 요청", description = "이메일로 6자리 인증 코드를 전송합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전송 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이메일 형식 오류/쿨다운"),
    })
    @PostMapping("/request")
    public ApiResponse<String> requestOtp(@Valid @RequestBody EmailOtpRequestDTO dto) {
        emailOtpAuthService.requestOtp(dto);
        return ApiResponse.ok("인증 코드가 전송되었습니다.");
    }

    @Operation(summary = "OTP 검증 및 로그인", description = "이메일 OTP를 검증하고 Access/Refresh 토큰을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검증 성공 + 로그인"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "OTP 만료/불일치/시도 횟수 초과"),
    })
    @PostMapping("/verify")
    public ApiResponse<AuthLoginResponseDTO> verifyOtp(
            @Valid @RequestBody EmailOtpVerifyRequestDTO dto,
            HttpServletResponse response) {
        AuthLoginResponseDTO result = emailOtpAuthService.verifyOtp(dto, response);
        return ApiResponse.ok(result);
    }
}
