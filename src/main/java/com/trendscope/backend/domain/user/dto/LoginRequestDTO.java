package com.trendscope.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequestDTO {

    @Schema(description = "로그인 아이디", example = "boatuser123")
    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(min = 5, max = 20, message = "아이디는 5~20자 사이여야 합니다.")
    private String username;

    @Schema(description = "로그인 비밀번호", example = "password123!")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(max = 100, message = "비밀번호가 너무 깁니다.")
    private String password;

    @Schema(description = "디바이스 식별자", example = "device_xyz_123")
    private String deviceId;
}