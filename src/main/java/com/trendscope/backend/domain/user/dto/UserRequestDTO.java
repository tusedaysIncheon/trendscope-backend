package com.trendscope.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDTO {

    // --- Validation Groups (검증 그룹 정의) ---
    public interface existGroup {}  // 중복 확인용
    public interface addGroup {}    // 회원가입용
    public interface updateGroup {} // 정보 수정용
    public interface deleteGroup {} // 회원 탈퇴용

    // --- 필드 정의 ---

    @Schema(description = "사용자 아이디", example = "boatuser123")
    @NotBlank(message = "아이디는 필수입니다.", groups = {addGroup.class, existGroup.class, deleteGroup.class})
    @Size(min = 5, max = 20, message = "아이디는 5자 이상 20자 이하로 작성해주세요.", groups = {addGroup.class, existGroup.class})
    // Zod: .string().min(5).max(20)
    private String username;

    @Schema(description = "이메일", example = "boat@gmail.com")
    @NotBlank(message = "이메일은 필수입니다.", groups = {addGroup.class, updateGroup.class})
    @Email(message = "유효한 이메일 주소를 입력해주세요.", groups = {addGroup.class, updateGroup.class})
    private String email;


}
