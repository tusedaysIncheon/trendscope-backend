package com.trendscope.backend.domain.analyze.dto;

import java.time.LocalDateTime;

public record AnalyzeJobShareResponseDTO(
        String token,
        String shareUrl,
        LocalDateTime expiresAt
) {
}
