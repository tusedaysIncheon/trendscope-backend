package com.trendscope.backend.domain.measurement.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record FashionRecommendationResponseDTO(
        String jobId,
        String measurementModel,
        JsonNode recommendation
) {
}

