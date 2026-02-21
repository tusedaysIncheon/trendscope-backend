package com.trendscope.backend.domain.analyze.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendscope.backend.domain.analyze.dto.AnalyzeJobStartRequestDTO;
import com.trendscope.backend.domain.analyze.entity.AnalyzeJobEntity;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import com.trendscope.backend.domain.analyze.repository.AnalyzeJobRepository;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.TicketLedgerReason;
import com.trendscope.backend.domain.user.entity.enums.SocialProviderType;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import com.trendscope.backend.domain.user.service.TicketLedgerService;
import com.trendscope.backend.global.exception.UpstreamServiceException;
import com.trendscope.backend.global.util.S3Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyzeJobServiceContractTest {

    @Mock
    private AnalyzeJobRepository analyzeJobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketLedgerService ticketLedgerService;

    @Mock
    private S3Util s3Util;

    @Mock
    private ModalAnalyzeClient modalAnalyzeClient;

    @Mock
    private AnalyzeShareTokenService analyzeShareTokenService;

    private AnalyzeJobService service;

    @BeforeEach
    void setUp() {
        service = new AnalyzeJobService(
                analyzeJobRepository,
                userRepository,
                ticketLedgerService,
                s3Util,
                modalAnalyzeClient,
                analyzeShareTokenService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "downloadUrlExpireMinutes", 30L);

        lenient().when(s3Util.createPresignedGetUrl(anyString(), any(Duration.class)))
                .thenAnswer(inv -> "https://get.local/" + inv.getArgument(0, String.class));
        lenient().when(s3Util.createPresignedPutUrl(anyString(), any(Duration.class)))
                .thenAnswer(inv -> "https://put.local/" + inv.getArgument(0, String.class));
    }

    @Test
    void buildModalPayloadUsesStoredMeasurementModelAndForcesPhotoPose() {
        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .jobId("job-quick")
                .mode(AnalyzeMode.QUICK_1VIEW)
                .status(AnalyzeJobStatus.QUEUED)
                .frontImageKey("in/front.jpg")
                .glbObjectKey("out/body.glb")
                .heightCm(175.0d)
                .weightKg(70.0d)
                .gender("male")
                .qualityMode("accurate")
                .normalizeWithAnny(true)
                .measurementModel("quick")
                .outputPose("A_POSE")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = ReflectionTestUtils.invokeMethod(service, "buildModalPayload", job);

        assertNotNull(payload);
        assertEquals("QUICK_1VIEW", payload.get("mode"));
        assertEquals("quick", payload.get("measurement_model"));
        assertEquals("PHOTO_POSE", payload.get("output_pose"));
    }

    @Test
    void buildModalPayloadInfersMeasurementModelFromModeWhenMissing() {
        AnalyzeJobEntity quickJob = AnalyzeJobEntity.builder()
                .jobId("job-q")
                .mode(AnalyzeMode.QUICK_1VIEW)
                .status(AnalyzeJobStatus.QUEUED)
                .frontImageKey("in/front.jpg")
                .glbObjectKey("out/body.glb")
                .normalizeWithAnny(true)
                .build();
        AnalyzeJobEntity premiumJob = AnalyzeJobEntity.builder()
                .jobId("job-p")
                .mode(AnalyzeMode.STANDARD_2VIEW)
                .status(AnalyzeJobStatus.QUEUED)
                .frontImageKey("in/front.jpg")
                .sideImageKey("in/side.jpg")
                .glbObjectKey("out/body.glb")
                .normalizeWithAnny(true)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> quickPayload = ReflectionTestUtils.invokeMethod(service, "buildModalPayload", quickJob);
        @SuppressWarnings("unchecked")
        Map<String, Object> premiumPayload = ReflectionTestUtils.invokeMethod(service, "buildModalPayload", premiumJob);

        assertNotNull(quickPayload);
        assertNotNull(premiumPayload);
        assertEquals("quick", quickPayload.get("measurement_model"));
        assertEquals("premium", premiumPayload.get("measurement_model"));
    }

    @Test
    void normalizeMeasurementModelRejectsModeMismatch() {
        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "normalizeMeasurementModel",
                        "quick",
                        AnalyzeMode.STANDARD_2VIEW
                )
        );
        assertTrue(ex1.getMessage().contains("measurementModel=quick"));

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "normalizeMeasurementModel",
                        "premium",
                        AnalyzeMode.QUICK_1VIEW
                )
        );
        assertTrue(ex2.getMessage().contains("measurementModel=premium"));
    }

    @Test
    void startRejectsWhenTicketHoldFails() {
        String username = "otp_user";
        String jobId = "job-premium-1";

        UserEntity user = UserEntity.builder()
                .id(11L)
                .username(username)
                .socialProviderType(SocialProviderType.EMAIL_OTP)
                .providerUserId("otp_user@example.com")
                .roleType(UserRoleType.USER)
                .quickTicketBalance(0)
                .premiumTicketBalance(1)
                .email("otp_user@example.com")
                .isLock(false)
                .build();

        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .jobId(jobId)
                .user(user)
                .mode(AnalyzeMode.STANDARD_2VIEW)
                .status(AnalyzeJobStatus.QUEUED)
                .frontImageKey("in/front.jpg")
                .sideImageKey("in/side.jpg")
                .glbObjectKey("out/body.glb")
                .build();

        AnalyzeJobStartRequestDTO dto = new AnalyzeJobStartRequestDTO();
        ReflectionTestUtils.setField(dto, "heightCm", 175.0d);
        ReflectionTestUtils.setField(dto, "weightKg", 70.0d);
        ReflectionTestUtils.setField(dto, "gender", "male");
        ReflectionTestUtils.setField(dto, "measurementModel", "premium");

        when(analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)).thenReturn(Optional.of(job));
        when(ticketLedgerService.holdForAnalyze(username, TicketType.PREMIUM, jobId))
                .thenThrow(new IllegalArgumentException("보유 티켓이 부족합니다."));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.start(username, jobId, dto)
        );
        assertTrue(ex.getMessage().contains("보유 티켓이 부족"));
        verify(analyzeJobRepository, never()).save(any(AnalyzeJobEntity.class));
    }

    @Test
    void processJobPreservesUpstreamErrorCodeAndDetail() {
        String username = "otp_user";
        String jobId = "job-upstream-error";

        UserEntity user = UserEntity.builder()
                .id(11L)
                .username(username)
                .socialProviderType(SocialProviderType.EMAIL_OTP)
                .providerUserId("otp_user@example.com")
                .roleType(UserRoleType.USER)
                .quickTicketBalance(1)
                .premiumTicketBalance(1)
                .email("otp_user@example.com")
                .isLock(false)
                .build();

        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .jobId(jobId)
                .user(user)
                .mode(AnalyzeMode.QUICK_1VIEW)
                .status(AnalyzeJobStatus.QUEUED)
                .frontImageKey("in/front.jpg")
                .glbObjectKey("out/body.glb")
                .measurementModel("quick")
                .build();

        when(analyzeJobRepository.findByJobId(jobId)).thenReturn(Optional.of(job));
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(ticketLedgerService.releaseHeldForAnalyze(username, TicketType.QUICK, jobId))
                .thenReturn(new com.trendscope.backend.domain.user.dto.TicketTransactionResponseDTO(
                        1L,
                        TicketType.QUICK,
                        TicketLedgerReason.RELEASE,
                        jobId,
                        1,
                        1,
                        1,
                        1,
                        2,
                        true,
                        LocalDateTime.now()
                ));
        when(modalAnalyzeClient.analyze(anyMap())).thenThrow(
                new UpstreamServiceException(
                        "MODAL_ENDPOINT_STOPPED",
                        "Modal endpoint가 중지 상태입니다.",
                        404
                )
        );

        ReflectionTestUtils.invokeMethod(service, "processJob", jobId);

        assertEquals(AnalyzeJobStatus.FAILED, job.getStatus());
        assertEquals("MODAL_ENDPOINT_STOPPED", job.getErrorCode());
        assertTrue(job.getErrorDetail().contains("중지"));
    }

    @Test
    void processJobUsesGenericErrorCodeForUnexpectedException() {
        String username = "otp_user";
        String jobId = "job-unexpected-error";

        UserEntity user = UserEntity.builder()
                .id(21L)
                .username(username)
                .socialProviderType(SocialProviderType.EMAIL_OTP)
                .providerUserId("otp_user@example.com")
                .roleType(UserRoleType.USER)
                .quickTicketBalance(1)
                .premiumTicketBalance(1)
                .email("otp_user@example.com")
                .isLock(false)
                .build();

        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .jobId(jobId)
                .user(user)
                .mode(AnalyzeMode.QUICK_1VIEW)
                .status(AnalyzeJobStatus.QUEUED)
                .frontImageKey("in/front.jpg")
                .glbObjectKey("out/body.glb")
                .measurementModel("quick")
                .build();

        when(analyzeJobRepository.findByJobId(jobId)).thenReturn(Optional.of(job));
        when(modalAnalyzeClient.analyze(anyMap())).thenThrow(new RuntimeException("boom"));

        ReflectionTestUtils.invokeMethod(service, "processJob", jobId);

        assertEquals(AnalyzeJobStatus.FAILED, job.getStatus());
        assertEquals("modal_call_failed", job.getErrorCode());
        assertTrue(job.getErrorDetail().contains("boom"));
    }
}
