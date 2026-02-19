package com.trendscope.backend.domain.analyze.controller;

import com.trendscope.backend.domain.analyze.dto.AnalyzeOneShotRequestDTO;
import com.trendscope.backend.domain.analyze.dto.AnalyzeOneShotResponseDTO;
import com.trendscope.backend.domain.analyze.service.AnalyzeOneShotService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/dev/analyze")
@Tag(name = "Analyze One-Shot API", description = "토큰 없이 파일 업로드 1회로 분석 결과 JSON을 반환하는 로컬/튜닝용 API")
public class AnalyzeOneShotController {

    private final AnalyzeOneShotService analyzeOneShotService;

    @Operation(summary = "원샷 분석", description = "정면/측면 이미지를 multipart로 업로드하면 Modal 분석까지 수행 후 JSON 결과를 즉시 반환합니다.")
    @PostMapping(value = "/one-shot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AnalyzeOneShotResponseDTO> analyzeOneShot(
            @Valid @ModelAttribute AnalyzeOneShotRequestDTO dto
    ) {
        return ApiResponse.ok(analyzeOneShotService.analyze(dto));
    }
}
