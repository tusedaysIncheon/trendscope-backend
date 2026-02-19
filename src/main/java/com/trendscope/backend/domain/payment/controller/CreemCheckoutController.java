package com.trendscope.backend.domain.payment.controller;

import com.trendscope.backend.domain.payment.dto.CreemCheckoutCreateRequestDTO;
import com.trendscope.backend.domain.payment.dto.CreemCheckoutCreateResponseDTO;
import com.trendscope.backend.domain.payment.service.CreemCheckoutService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments/creem")
@Tag(name = "Creem Checkout API", description = "Creem 결제 세션 생성 API")
public class CreemCheckoutController {

    private final CreemCheckoutService creemCheckoutService;

    @Operation(summary = "Creem checkout 생성", description = "티켓 타입/수량으로 Creem checkout URL을 생성합니다.")
    @PostMapping("/checkout")
    public ApiResponse<CreemCheckoutCreateResponseDTO> createCheckout(
            @Valid @RequestBody CreemCheckoutCreateRequestDTO dto
    ) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("인증 사용자 정보를 찾을 수 없습니다.");
        }

        CreemCheckoutCreateResponseDTO response = creemCheckoutService.createCheckout(username, dto);
        return ApiResponse.ok(response);
    }
}
