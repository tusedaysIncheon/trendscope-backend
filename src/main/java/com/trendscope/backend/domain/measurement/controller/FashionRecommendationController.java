package com.trendscope.backend.domain.measurement.controller;

import com.trendscope.backend.domain.measurement.dto.FashionRecommendationHistoryDetailResponseDTO;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationHistoryListResponseDTO;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationRequestDTO;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationResponseDTO;
import com.trendscope.backend.domain.measurement.service.FashionRecommendationService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/measurement")
@Tag(name = "Fashion Recommendation API", description = "측정 결과를 기반으로 OpenAI 패션 추천 JSON을 생성합니다.")
public class FashionRecommendationController {

    private final FashionRecommendationService fashionRecommendationService;

    @Operation(summary = "패션 추천 생성", description = "완료된 analyze job 결과를 OpenAI로 보내 고정 JSON 스키마 추천을 반환합니다.")
    @PostMapping("/fashion-recommendation")
    public ApiResponse<FashionRecommendationResponseDTO> recommend(
            @Valid @RequestBody FashionRecommendationRequestDTO dto
    ) {
        return ApiResponse.ok(fashionRecommendationService.recommend(currentUsername(), dto));
    }

    @Operation(summary = "패션 추천 이력 목록", description = "내 추천 이력을 최신순으로 조회합니다.")
    @GetMapping("/fashion-recommendation/history")
    public ApiResponse<FashionRecommendationHistoryListResponseDTO> history(
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(fashionRecommendationService.getHistory(currentUsername(), size));
    }

    @Operation(summary = "패션 추천 이력 상세", description = "userSeq로 단건 추천 이력을 조회합니다.")
    @GetMapping("/fashion-recommendation/history/{userSeq}")
    public ApiResponse<FashionRecommendationHistoryDetailResponseDTO> historyDetail(
            @PathVariable Long userSeq
    ) {
        return ApiResponse.ok(fashionRecommendationService.getHistoryDetail(currentUsername(), userSeq));
    }

    private String currentUsername() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("인증 사용자 정보를 찾을 수 없습니다.");
        }
        return username;
    }
}
