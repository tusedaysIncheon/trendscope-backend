package com.trendscope.backend.domain.user.dto;

public record AuthLoginResponseDTO(
        String accessToken,
        UserResponseDTO user
) {
}
