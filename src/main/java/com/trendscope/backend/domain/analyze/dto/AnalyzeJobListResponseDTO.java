package com.trendscope.backend.domain.analyze.dto;

import java.util.List;

public record AnalyzeJobListResponseDTO(
        String username,
        List<AnalyzeJobListItemDTO> jobs
) {
}
