package com.trendscope.backend.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendscope.backend.domain.user.dto.TicketTransactionResponseDTO;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.SocialProviderType;
import com.trendscope.backend.domain.user.entity.enums.TicketLedgerReason;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import com.trendscope.backend.domain.user.service.TicketLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CreemWebhookServiceTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String QUICK_PRODUCT_ID = "prod_quick_test";
    private static final String PREMIUM_PRODUCT_ID = "prod_premium_test";

    private TicketLedgerService ticketLedgerService;
    private UserRepository userRepository;
    private CreemWebhookService service;

    @BeforeEach
    void setUp() {
        ticketLedgerService = mock(TicketLedgerService.class);
        userRepository = mock(UserRepository.class);

        service = new CreemWebhookService(
                new ObjectMapper(),
                ticketLedgerService,
                userRepository
        );
        ReflectionTestUtils.setField(service, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(service, "quickProductId", QUICK_PRODUCT_ID);
        ReflectionTestUtils.setField(service, "premiumProductId", PREMIUM_PRODUCT_ID);
    }

    @Test
    void processWebhook_processedWhenValidAndFirstTime() {
        String payload = """
                {
                  "id":"evt_001",
                  "eventType":"checkout.completed",
                  "object":{
                    "id":"chk_001",
                    "request_id":"username:OTP_user_01",
                    "order":{
                      "id":"ord_001",
                      "product":"prod_premium_test",
                      "units":2
                    },
                    "customer":{"email":"user@example.com"}
                  }
                }
                """;
        String signature = sign(payload, SECRET);

        when(ticketLedgerService.purchase(eq("OTP_user_01"), eq(TicketType.PREMIUM), eq(2), eq("ord_001")))
                .thenReturn(response(true, TicketType.PREMIUM, 2, "ord_001"));

        String result = service.processWebhook(payload, signature);

        assertEquals("PROCESSED", result);
        verify(ticketLedgerService, times(1))
                .purchase(eq("OTP_user_01"), eq(TicketType.PREMIUM), eq(2), eq("ord_001"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void processWebhook_duplicateWhenAlreadyApplied() {
        String payload = """
                {
                  "id":"evt_002",
                  "eventType":"checkout.completed",
                  "object":{
                    "id":"chk_002",
                    "request_id":"username:OTP_user_02",
                    "order":{
                      "id":"ord_002",
                      "product":"prod_quick_test",
                      "units":1
                    }
                  }
                }
                """;
        String signature = sign(payload, SECRET);

        when(ticketLedgerService.purchase(eq("OTP_user_02"), eq(TicketType.QUICK), eq(1), eq("ord_002")))
                .thenReturn(response(false, TicketType.QUICK, 1, "ord_002"));

        String result = service.processWebhook(payload, signature);

        assertEquals("IGNORED_DUPLICATE", result);
        verify(ticketLedgerService, times(1))
                .purchase(eq("OTP_user_02"), eq(TicketType.QUICK), eq(1), eq("ord_002"));
    }

    @Test
    void processWebhook_resolvesUsernameByCustomerEmailWhenRequestIdMissing() {
        String payload = """
                {
                  "id":"evt_003",
                  "eventType":"checkout.completed",
                  "object":{
                    "id":"chk_003",
                    "order":{
                      "id":"ord_003",
                      "product":"prod_quick_test",
                      "units":1
                    },
                    "customer":{"email":"email-user@example.com"}
                  }
                }
                """;
        String signature = sign(payload, SECRET);

        UserEntity user = UserEntity.builder()
                .username("OTP_email_user")
                .email("email-user@example.com")
                .socialProviderType(SocialProviderType.EMAIL_OTP)
                .providerUserId("email-user@example.com")
                .roleType(UserRoleType.USER)
                .quickTicketBalance(0)
                .premiumTicketBalance(0)
                .isLock(false)
                .build();

        when(userRepository.findFirstByEmailOrderByIdAsc("email-user@example.com"))
                .thenReturn(Optional.of(user));
        when(ticketLedgerService.purchase(eq("OTP_email_user"), eq(TicketType.QUICK), eq(1), eq("ord_003")))
                .thenReturn(response(true, TicketType.QUICK, 1, "ord_003"));

        String result = service.processWebhook(payload, signature);

        assertEquals("PROCESSED", result);
        verify(userRepository, times(1)).findFirstByEmailOrderByIdAsc("email-user@example.com");
        verify(ticketLedgerService, times(1))
                .purchase(eq("OTP_email_user"), eq(TicketType.QUICK), eq(1), eq("ord_003"));
    }

    @Test
    void processWebhook_throwsSecurityExceptionWhenSignatureInvalid() {
        String payload = """
                {
                  "id":"evt_004",
                  "eventType":"checkout.completed",
                  "object":{"id":"chk_004"}
                }
                """;

        assertThrows(SecurityException.class, () -> service.processWebhook(payload, "invalid_signature"));
        verifyNoInteractions(ticketLedgerService);
    }

    @Test
    void processWebhook_ignoresUnsupportedEvent() {
        String payload = """
                {
                  "id":"evt_005",
                  "eventType":"checkout.failed",
                  "object":{"id":"chk_005"}
                }
                """;
        String signature = sign(payload, SECRET);

        String result = service.processWebhook(payload, signature);

        assertEquals("IGNORED_UNSUPPORTED_EVENT", result);
        verifyNoInteractions(ticketLedgerService);
    }

    @Test
    void processWebhook_processedForSnakeCaseDataObjectAndV1Signature() {
        String payload = """
                {
                  "id":"evt_006",
                  "event_type":"checkout.completed",
                  "data":{
                    "object":{
                      "id":"chk_006",
                      "request_id":"username:OTP_user_06",
                      "order":{
                        "id":"ord_006",
                        "product_id":"prod_quick_test",
                        "quantity":"1"
                      }
                    }
                  }
                }
                """;
        String signature = "t=1739200000,v1=" + sign(payload, SECRET);

        when(ticketLedgerService.purchase(eq("OTP_user_06"), eq(TicketType.QUICK), eq(1), eq("ord_006")))
                .thenReturn(response(true, TicketType.QUICK, 1, "ord_006"));

        String result = service.processWebhook(payload, signature);

        assertEquals("PROCESSED", result);
        verify(ticketLedgerService, times(1))
                .purchase(eq("OTP_user_06"), eq(TicketType.QUICK), eq(1), eq("ord_006"));
    }

    @Test
    void processWebhook_processedWhenTicketTypeComesFromMetadata() {
        String payload = """
                {
                  "id":"evt_007",
                  "eventType":"checkout.completed",
                  "object":{
                    "id":"chk_007",
                    "request_id":"username:OTP_user_07",
                    "metadata":{"ticket_type":"PREMIUM","quantity":"2"}
                  }
                }
                """;
        String signature = sign(payload, SECRET);

        when(ticketLedgerService.purchase(eq("OTP_user_07"), eq(TicketType.PREMIUM), eq(2), eq("chk_007")))
                .thenReturn(response(true, TicketType.PREMIUM, 2, "chk_007"));

        String result = service.processWebhook(payload, signature);

        assertEquals("PROCESSED", result);
        verify(ticketLedgerService, times(1))
                .purchase(eq("OTP_user_07"), eq(TicketType.PREMIUM), eq(2), eq("chk_007"));
    }

    private TicketTransactionResponseDTO response(boolean applied, TicketType ticketType, int quantity, String refId) {
        return new TicketTransactionResponseDTO(
                1L,
                ticketType,
                TicketLedgerReason.PURCHASE,
                refId,
                quantity,
                quantity,
                0,
                0,
                quantity,
                applied,
                LocalDateTime.now()
        );
    }

    private String sign(String payload, String secret) {
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
            throw new IllegalStateException("signature 생성 실패", e);
        }
    }
}
