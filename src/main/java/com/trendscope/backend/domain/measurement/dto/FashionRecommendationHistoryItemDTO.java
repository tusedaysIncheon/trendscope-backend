package com.trendscope.backend.domain.measurement.dto;

import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;

import java.time.LocalDateTime;

public record FashionRecommendationHistoryItemDTO(
        Long userSeq,
        String jobId,
        AnalyzeMode mode,
        String measurementModel,
        String frontImageKey,
        String sideImageKey,
        String glbObjectKey,
        LocalDateTime createdDate
) {
}
