package com.trendscope.backend.domain.analyze.controller;

import com.trendscope.backend.domain.analyze.dto.AnalyzeSharedJobResponseDTO;
import com.trendscope.backend.domain.analyze.service.AnalyzeJobService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/share/analyze")
@Tag(name = "Analyze Share API", description = "측정 결과 공유 링크 조회 API")
public class AnalyzeShareController {

    private final AnalyzeJobService analyzeJobService;

    @Operation(summary = "공유 결과 조회", description = "공유 토큰으로 공개 가능한 측정 결과를 조회합니다.")
    @GetMapping("/{token}")
    public ApiResponse<AnalyzeSharedJobResponseDTO> getSharedResult(@PathVariable String token) {
        return ApiResponse.ok(analyzeJobService.getSharedJob(token));
    }
}
