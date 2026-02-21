package com.trendscope.backend.domain.analyze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;

import java.time.LocalDateTime;

public record AnalyzeSharedJobResponseDTO(
        String jobId,
        AnalyzeMode mode,
        AnalyzeJobStatus status,
        String glbDownloadUrl,
        Double heightCm,
        Double weightKg,
        String gender,
        String measurementModel,
        JsonNode result,
        LocalDateTime completedAt,
        LocalDateTime createdDate
) {
}
