package com.trendscope.backend.domain.analyze.controller;

import com.trendscope.backend.domain.analyze.dto.*;
import com.trendscope.backend.domain.analyze.service.AnalyzeJobService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/analyze/jobs")
@Tag(name = "Analyze Job API", description = "측정 파이프라인 API (업로드 URL 발급 / 시작 / 상태 조회)")
public class AnalyzeJobController {

    private final AnalyzeJobService analyzeJobService;

    @Operation(summary = "업로드 URL 발급", description = "정면/측면/GLB 파일 업로드용 presigned URL과 jobId를 발급합니다.")
    @PostMapping("/upload-urls")
    public ApiResponse<AnalyzeUploadUrlsResponseDTO> issueUploadUrls(
            @Valid @RequestBody AnalyzeUploadUrlsRequestDTO dto
    ) {
        return ApiResponse.ok(analyzeJobService.issueUploadUrls(currentUsername(), dto));
    }

    @Operation(summary = "측정 시작", description = "업로드 완료된 이미지로 Modal 분석 작업을 비동기 실행합니다.")
    @PostMapping("/{jobId}/start")
    public ApiResponse<AnalyzeJobStartResponseDTO> startJob(
            @PathVariable String jobId,
            @Valid @RequestBody AnalyzeJobStartRequestDTO dto
    ) {
        return ApiResponse.ok(analyzeJobService.start(currentUsername(), jobId, dto));
    }

    @Operation(summary = "측정 상태 조회", description = "QUEUED/RUNNING/COMPLETED/FAILED 상태와 결과를 조회합니다.")
    @GetMapping("/{jobId}")
    public ApiResponse<AnalyzeJobStatusResponseDTO> getJob(@PathVariable String jobId) {
        return ApiResponse.ok(analyzeJobService.getJob(currentUsername(), jobId));
    }

    @Operation(summary = "결과 공유 링크 발급", description = "완료된 측정 결과에 대한 공개 공유 링크를 발급합니다.")
    @PostMapping("/{jobId}/share")
    public ApiResponse<AnalyzeJobShareResponseDTO> createShareLink(@PathVariable String jobId) {
        return ApiResponse.ok(analyzeJobService.issueShareLink(currentUsername(), jobId));
    }

    @Operation(summary = "내 측정 목록", description = "최근 측정 작업 목록을 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<AnalyzeJobListResponseDTO> myJobs(@RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(analyzeJobService.getMyJobs(currentUsername(), size));
    }

    private String currentUsername() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("인증 사용자 정보를 찾을 수 없습니다.");
        }
        return username;
    }
}
