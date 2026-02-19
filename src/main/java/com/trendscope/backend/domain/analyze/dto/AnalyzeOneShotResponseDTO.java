package com.trendscope.backend.domain.analyze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnalyzeOneShotResponseDTO {
    private String requestId;
    private AnalyzeMode mode;
    private String frontImageKey;
    private String sideImageKey;
    private String glbObjectKey;
    private String glbDownloadUrl;
    private String debugGlbObjectKey;
    private String debugGlbDownloadUrl;
    private JsonNode modalResponse;
}
