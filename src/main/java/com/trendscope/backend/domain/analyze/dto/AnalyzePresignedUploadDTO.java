package com.trendscope.backend.domain.analyze.dto;

public record AnalyzePresignedUploadDTO(
        String fileKey,
        String uploadUrl
) {
}
