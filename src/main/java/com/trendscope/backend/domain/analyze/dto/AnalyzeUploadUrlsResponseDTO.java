package com.trendscope.backend.domain.analyze.dto;

import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;

public record AnalyzeUploadUrlsResponseDTO(
        String jobId,
        AnalyzeMode mode,
        AnalyzeJobStatus status,
        AnalyzePresignedUploadDTO frontImage,
        AnalyzePresignedUploadDTO sideImage,
        AnalyzePresignedUploadDTO glbObject
) {
}
