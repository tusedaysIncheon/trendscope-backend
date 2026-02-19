package com.trendscope.backend.domain.analyze.dto;

import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;

import java.time.LocalDateTime;

public record AnalyzeJobStartResponseDTO(
        String jobId,
        AnalyzeMode mode,
        AnalyzeJobStatus status,
        LocalDateTime queuedAt
) {
}
