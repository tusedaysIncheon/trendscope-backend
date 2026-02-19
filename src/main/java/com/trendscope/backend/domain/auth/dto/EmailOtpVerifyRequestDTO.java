package com.trendscope.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmailOtpVerifyRequestDTO {

    @Schema(description = "OTP 수신 이메일", example = "user@example.com")
    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "유효한 이메일 주소를 입력해주세요.")
    private String email;

    @Schema(description = "6자리 OTP 코드", example = "123456")
    @NotBlank(message = "인증 코드를 입력해주세요.")
    @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 6자리 숫자여야 합니다.")
    private String code;

    @Schema(description = "디바이스 식별자", example = "device_xyz_123")
    private String deviceId;
}
