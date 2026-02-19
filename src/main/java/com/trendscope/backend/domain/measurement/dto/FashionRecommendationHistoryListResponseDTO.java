package com.trendscope.backend.domain.measurement.dto;

import java.util.List;

public record FashionRecommendationHistoryListResponseDTO(
        String username,
        List<FashionRecommendationHistoryItemDTO> histories
) {
}
