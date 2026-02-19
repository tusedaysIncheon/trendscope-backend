package com.trendscope.backend.global.jwt.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class RefreshRequestDTO {

        @NotBlank
        private String refreshToken;
}
