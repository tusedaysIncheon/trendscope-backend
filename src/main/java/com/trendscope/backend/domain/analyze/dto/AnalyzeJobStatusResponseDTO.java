package com.trendscope.backend.domain.analyze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;

import java.time.LocalDateTime;

public record AnalyzeJobStatusResponseDTO(
        String jobId,
        AnalyzeMode mode,
        AnalyzeJobStatus status,
        String frontImageKey,
        String sideImageKey,
        String glbObjectKey,
        String glbDownloadUrl,
        Double heightCm,
        Double weightKg,
        String gender,
        String qualityMode,
        Boolean normalizeWithAnny,
        String measurementModel,
        String outputPose,
        String errorCode,
        String errorDetail,
        JsonNode result,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdDate,
        LocalDateTime updatedDate
) {
}
