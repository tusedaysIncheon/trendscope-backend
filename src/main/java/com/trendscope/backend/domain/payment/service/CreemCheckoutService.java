package com.trendscope.backend.domain.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendscope.backend.domain.payment.dto.CreemCheckoutCreateRequestDTO;
import com.trendscope.backend.domain.payment.dto.CreemCheckoutCreateResponseDTO;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreemCheckoutService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${creem.base-url:https://test-api.creem.io}")
    private String creemBaseUrl;

    @Value("${creem.api-key:}")
    private String creemApiKey;

    @Value("${creem.products.quick:}")
    private String quickProductId;

    @Value("${creem.products.premium:}")
    private String premiumProductId;

    @Value("${creem.checkout-success-url:}")
    private String checkoutSuccessUrl;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public CreemCheckoutCreateResponseDTO createCheckout(String username, CreemCheckoutCreateRequestDTO dto) {
        if (dto.getTicketType() == null) {
            throw new IllegalArgumentException("ticketType은 필수입니다.");
        }
        if (!hasText(creemApiKey)) {
            throw new IllegalArgumentException("creem.api-key가 설정되지 않았습니다.");
        }

        UserEntity user = userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        int quantity = normalizeQuantity(dto.getQuantity());
        String productId = resolveProductId(dto.getTicketType());
        String requestId = "username:" + user.getUsername();
        String successUrl = resolveSuccessUrl(dto.getSuccessUrl());

        Map<String, Object> body = new HashMap<>();
        body.put("product_id", productId);
        body.put("request_id", requestId);
        body.put("units", quantity);
        body.put("success_url", successUrl);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", user.getUsername());
        metadata.put("ticket_type", dto.getTicketType().name());
        metadata.put("quantity", quantity);
        body.put("metadata", metadata);

        Map<String, Object> customer = new HashMap<>();
        customer.put("email", user.getEmail());
        body.put("customer", customer);

        RestClient client = RestClient.builder()
                .baseUrl(creemBaseUrl)
                .build();

        try {
            String rawResponse = client.post()
                    .uri("/v1/checkouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-api-key", creemApiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            String checkoutId = text(root, "id");
            String checkoutUrl = text(root, "checkout_url");
            if (!hasText(checkoutId) && root.path("data").isObject()) {
                checkoutId = text(root.path("data"), "id");
            }
            if (!hasText(checkoutUrl) && root.path("data").isObject()) {
                checkoutUrl = text(root.path("data"), "checkout_url");
            }

            if (!hasText(checkoutUrl)) {
                throw new IllegalArgumentException("Creem 응답에 checkout_url이 없습니다.");
            }

            return new CreemCheckoutCreateResponseDTO(
                    checkoutId,
                    checkoutUrl,
                    requestId,
                    dto.getTicketType(),
                    quantity,
                    productId
            );
        } catch (RestClientResponseException e) {
            log.warn("Creem checkout 생성 실패 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalArgumentException("Creem checkout 생성 실패: " + e.getStatusCode());
        } catch (Exception e) {
            throw new IllegalArgumentException("Creem checkout 생성 중 오류가 발생했습니다.", e);
        }
    }

    private String resolveProductId(TicketType ticketType) {
        String productId = ticketType == TicketType.QUICK ? quickProductId : premiumProductId;
        if (!hasText(productId)) {
            throw new IllegalArgumentException("티켓 타입에 대한 Creem product id가 설정되지 않았습니다: " + ticketType);
        }
        return productId.trim();
    }

    private String resolveSuccessUrl(String successUrl) {
        if (hasText(successUrl)) {
            return successUrl.trim();
        }
        if (hasText(checkoutSuccessUrl)) {
            return checkoutSuccessUrl.trim();
        }
        return frontendBaseUrl + "/payment/success";
    }

    private int normalizeQuantity(Integer quantity) {
        int value = quantity == null ? 1 : quantity;
        if (value <= 0) {
            throw new IllegalArgumentException("quantity는 1 이상이어야 합니다.");
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode target = node.path(field);
        if (target.isMissingNode() || target.isNull()) {
            return "";
        }
        return target.asText("");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
