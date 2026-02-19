package com.trendscope.backend.domain.measurement.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;

import java.time.LocalDateTime;

public record FashionRecommendationHistoryDetailResponseDTO(
        Long userSeq,
        String jobId,
        AnalyzeMode mode,
        String measurementModel,
        String frontImageKey,
        String sideImageKey,
        String glbObjectKey,
        JsonNode result,
        JsonNode recommendation,
        String llmModel,
        String promptVersion,
        LocalDateTime createdDate
) {
}
