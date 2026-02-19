package com.trendscope.backend.domain.payment.controller;

import com.trendscope.backend.domain.payment.service.CreemWebhookService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments/creem")
@Tag(name = "Creem Payment Webhook API", description = "Creem 웹훅 수신/검증 API")
public class CreemWebhookController {

    private final CreemWebhookService creemWebhookService;

    @Operation(summary = "Creem 웹훅", description = "결제 완료 웹훅을 검증하고 티켓을 적립합니다.")
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<String>> webhook(
            @RequestHeader HttpHeaders headers,
            @RequestBody String rawPayload
    ) {
        try {
            String signature = resolveSignature(headers);
            String result = creemWebhookService.processWebhook(rawPayload, signature);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (SecurityException e) {
            log.warn("Creem webhook signature 검증 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail("UNAUTHORIZED", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Creem webhook 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("BAD_REQUEST", e.getMessage()));
        } catch (Exception e) {
            log.error("Creem webhook 처리 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail("INTERNAL_SERVER_ERROR", "웹훅 처리 중 서버 오류가 발생했습니다."));
        }
    }

    private String resolveSignature(HttpHeaders headers) {
        String signature = headers.getFirst("creem-signature");
        if (hasText(signature)) {
            return signature;
        }
        signature = headers.getFirst("x-creem-signature");
        if (hasText(signature)) {
            return signature;
        }
        signature = headers.getFirst("creem_signature");
        if (hasText(signature)) {
            return signature;
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
