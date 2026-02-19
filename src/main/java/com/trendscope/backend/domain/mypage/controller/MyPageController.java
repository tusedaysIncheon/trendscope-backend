package com.trendscope.backend.domain.mypage.controller;

import com.trendscope.backend.domain.mypage.dto.MyPageSummaryResponseDTO;
import com.trendscope.backend.domain.mypage.service.MyPageService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/mypage")
@Tag(name = "MyPage API", description = "마이페이지 요약 조회 API")
public class MyPageController {

    private final MyPageService myPageService;

    @Operation(summary = "마이페이지 요약", description = "티켓 잔액/원장과 최근 측정 목록을 함께 반환합니다.")
    @GetMapping("/summary")
    public ApiResponse<MyPageSummaryResponseDTO> summary(
            @RequestParam(defaultValue = "20") int ticketSize,
            @RequestParam(defaultValue = "20") int analyzeSize
    ) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("인증 사용자 정보를 찾을 수 없습니다.");
        }
        return ApiResponse.ok(myPageService.getSummary(username, ticketSize, analyzeSize));
    }
}
