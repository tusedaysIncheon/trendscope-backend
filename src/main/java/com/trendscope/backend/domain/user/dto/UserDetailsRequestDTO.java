package com.trendscope.backend.domain.user.dto;

import com.trendscope.backend.domain.user.entity.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class UserDetailsRequestDTO {

    public Gender gender;
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자 사이여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임은 한글, 영문, 숫자만 가능합니다.")
    private String nickname;

    @NotNull
    @DecimalMin("1.0")
    private BigDecimal height;
    @NotNull
    @DecimalMin("1.0")
    private BigDecimal weight;
}
