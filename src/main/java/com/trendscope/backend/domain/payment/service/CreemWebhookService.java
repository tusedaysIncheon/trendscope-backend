package com.trendscope.backend.domain.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendscope.backend.domain.user.dto.TicketTransactionResponseDTO;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import com.trendscope.backend.domain.user.service.TicketLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreemWebhookService {

    private static final String TARGET_EVENT_TYPE = "checkout.completed";

    private final ObjectMapper objectMapper;
    private final TicketLedgerService ticketLedgerService;
    private final UserRepository userRepository;

    @Value("${creem.webhook-secret:}")
    private String webhookSecret;

    @Value("${creem.products.quick:}")
    private String quickProductId;

    @Value("${creem.products.premium:}")
    private String premiumProductId;

    @Transactional
    public String processWebhook(String rawPayload, String signatureHeader) {
        if (!isValidSignature(rawPayload, signatureHeader)) {
            throw new SecurityException("유효하지 않은 Creem webhook signature 입니다.");
        }

        JsonNode root = parseJson(rawPayload);
        String eventType = resolveEventType(root);
        if (!isSupportedEvent(eventType)) {
            log.info("Creem webhook ignored. unsupported eventType={}", eventType);
            return "IGNORED_UNSUPPORTED_EVENT";
        }

        JsonNode object = resolveEventObject(root);
        TicketType ticketType = resolveTicketType(object);
        if (ticketType == null) {
            log.warn("Creem webhook ignored. unsupported product id");
            return "IGNORED_UNSUPPORTED_PRODUCT";
        }

        String username = resolveUsername(root, object);
        if (username == null || username.isBlank()) {
            log.warn("Creem webhook ignored. username not resolved");
            return "IGNORED_USER_NOT_FOUND";
        }

        int quantity = resolveQuantity(object);
        String refId = resolveRefId(root, object);
        if (refId == null || refId.isBlank()) {
            throw new IllegalArgumentException("Creem webhook refId를 찾을 수 없습니다.");
        }

        TicketTransactionResponseDTO result = ticketLedgerService.purchase(username, ticketType, quantity, refId);
        if (Boolean.TRUE.equals(result.applied())) {
            return "PROCESSED";
        }
        return "IGNORED_DUPLICATE";
    }

    private JsonNode parseJson(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Creem webhook payload JSON 파싱 실패", e);
        }
    }

    private boolean isValidSignature(String rawPayload, String signatureHeader) {
        if (!hasText(webhookSecret)) {
            log.error("creem.webhook-secret이 비어있어 signature 검증 불가");
            return false;
        }
        if (!hasText(signatureHeader)) {
            return false;
        }

        String expected = hmacSha256Hex(rawPayload, webhookSecret);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        for (String candidate : extractSignatureCandidates(signatureHeader)) {
            String normalized = normalizeSignature(candidate);
            if (!hasText(normalized)) {
                continue;
            }
            if (MessageDigest.isEqual(
                    expectedBytes,
                    normalized.getBytes(StandardCharsets.UTF_8)
            )) {
                return true;
            }
        }
        return false;
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Creem signature 검증 초기화 실패", e);
        }
    }

    private TicketType resolveTicketType(JsonNode object) {
        String metadataType = firstText(object.path("metadata"), "ticket_type", "ticketType");
        TicketType fromMetadata = fromTicketTypeText(metadataType);
        if (fromMetadata != null) {
            return fromMetadata;
        }

        String orderProduct = firstText(object.path("order"), "product", "product_id", "productId", "id");
        if (!hasText(orderProduct)) {
            orderProduct = text(object.path("order").path("product"), "id");
        }
        if (matchesProduct(orderProduct, quickProductId)) {
            return TicketType.QUICK;
        }
        if (matchesProduct(orderProduct, premiumProductId)) {
            return TicketType.PREMIUM;
        }

        String productId = firstText(object, "product_id", "productId");
        if (!hasText(productId)) {
            productId = text(object.path("product"), "id");
        }
        if (!hasText(productId) && object.path("product").isTextual()) {
            productId = object.path("product").asText("");
        }
        if (matchesProduct(productId, quickProductId)) {
            return TicketType.QUICK;
        }
        if (matchesProduct(productId, premiumProductId)) {
            return TicketType.PREMIUM;
        }
        return null;
    }

    private boolean matchesProduct(String actual, String expected) {
        return hasText(actual) && hasText(expected) && actual.trim().equals(expected.trim());
    }

    private String resolveUsername(JsonNode root, JsonNode object) {
        String requestId = firstText(object, "request_id", "requestId");
        if (!hasText(requestId)) {
            requestId = firstText(root, "request_id", "requestId");
        }
        if (hasText(requestId)) {
            String normalized = requestId.trim();
            if (normalized.startsWith("username:")) {
                return normalized.substring("username:".length()).trim();
            }
            return normalized;
        }

        JsonNode metadata = object.path("metadata");
        String metadataUser = firstText(metadata,
                "username",
                "user_id",
                "userId",
                "internal_user_id",
                "reference_id",
                "referenceId"
        );
        if (hasText(metadataUser)) {
            return metadataUser.trim();
        }

        JsonNode orderMetadata = object.path("order").path("metadata");
        String orderMetadataUser = firstText(orderMetadata,
                "username",
                "user_id",
                "userId",
                "internal_user_id",
                "reference_id",
                "referenceId"
        );
        if (hasText(orderMetadataUser)) {
            return orderMetadataUser.trim();
        }

        String email = firstText(object.path("customer"), "email", "email_address", "emailAddress");
        if (!hasText(email)) {
            email = firstText(object, "customer_email", "customerEmail", "email");
        }
        if (hasText(email)) {
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            return userRepository.findFirstByEmailOrderByIdAsc(normalizedEmail)
                    .map(UserEntity::getUsername)
                    .orElse(null);
        }

        return null;
    }

    private int resolveQuantity(JsonNode object) {
        Integer orderUnits = firstPositiveInt(object.path("order"), "units", "quantity");
        if (orderUnits != null) {
            return orderUnits;
        }

        Integer units = firstPositiveInt(object, "units", "quantity");
        if (units != null) {
            return units;
        }

        Integer metadataQty = firstPositiveInt(object.path("metadata"), "quantity", "units");
        if (metadataQty != null) {
            return metadataQty;
        }

        return 1;
    }

    private String resolveRefId(JsonNode root, JsonNode object) {
        String orderId = firstText(object.path("order"), "id", "order_id", "orderId");
        if (hasText(orderId)) {
            return orderId;
        }
        String checkoutId = firstText(object, "id", "checkout_id", "checkoutId");
        if (hasText(checkoutId)) {
            return checkoutId;
        }
        String eventId = text(root, "id");
        if (hasText(eventId)) {
            return eventId;
        }
        return null;
    }

    private String resolveEventType(JsonNode root) {
        return firstText(root, "eventType", "event_type", "type");
    }

    private boolean isSupportedEvent(String eventType) {
        return hasText(eventType) && TARGET_EVENT_TYPE.equalsIgnoreCase(eventType.trim());
    }

    private JsonNode resolveEventObject(JsonNode root) {
        JsonNode object = root.path("object");
        if (object.isObject()) {
            return object;
        }
        JsonNode data = root.path("data");
        JsonNode dataObject = data.path("object");
        if (dataObject.isObject()) {
            return dataObject;
        }
        if (data.isObject()) {
            return data;
        }
        return object;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            String value = text(node, field);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer firstPositiveInt(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode target = node.path(field);
            if (target.canConvertToInt() && target.asInt() > 0) {
                return target.asInt();
            }
            if (target.isTextual()) {
                try {
                    int parsed = Integer.parseInt(target.asText("").trim());
                    if (parsed > 0) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // no-op
                }
            }
        }
        return null;
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

    private TicketType fromTicketTypeText(String ticketTypeText) {
        if (!hasText(ticketTypeText)) {
            return null;
        }
        String normalized = ticketTypeText.trim().toUpperCase(Locale.ROOT);
        if ("QUICK".equals(normalized)) {
            return TicketType.QUICK;
        }
        if ("PREMIUM".equals(normalized)) {
            return TicketType.PREMIUM;
        }
        return null;
    }

    private List<String> extractSignatureCandidates(String signatureHeader) {
        List<String> candidates = new ArrayList<>();
        candidates.add(signatureHeader);

        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!hasText(trimmed)) {
                continue;
            }
            candidates.add(trimmed);

            int idx = trimmed.indexOf('=');
            if (idx > 0 && idx < trimmed.length() - 1) {
                String value = trimmed.substring(idx + 1).trim();
                if (hasText(value)) {
                    candidates.add(value);
                }
            }
        }
        return candidates;
    }

    private String normalizeSignature(String candidate) {
        if (!hasText(candidate)) {
            return "";
        }

        String value = candidate.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("sha256=")) {
            value = value.substring("sha256=".length());
        }
        if (value.startsWith("v1=")) {
            value = value.substring("v1=".length());
        }
        if (value.matches("^[0-9a-f]{64}$")) {
            return value;
        }
        return "";
    }
}
