package com.trendscope.backend.domain.measurement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendscope.backend.domain.analyze.entity.AnalyzeJobEntity;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import com.trendscope.backend.domain.analyze.repository.AnalyzeJobRepository;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationRequestDTO;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationResponseDTO;
import com.trendscope.backend.domain.measurement.entity.MeasurementRecommendationHistoryEntity;
import com.trendscope.backend.domain.measurement.repository.MeasurementRecommendationHistoryRepository;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.SocialProviderType;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FashionRecommendationServiceTest {

    @Mock
    private AnalyzeJobRepository analyzeJobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeasurementRecommendationHistoryRepository historyRepository;

    @Mock
    private OpenAiFashionClient openAiFashionClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FashionRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new FashionRecommendationService(
                analyzeJobRepository,
                userRepository,
                historyRepository,
                openAiFashionClient,
                objectMapper
        );
    }

    @Test
    void quickModelSendsOnlySuccessAndLengthsToOpenAi() throws Exception {
        String username = "otp_user";
        String jobId = "job-quick-1";
        FashionRecommendationRequestDTO dto = new FashionRecommendationRequestDTO();
        ReflectionTestUtils.setField(dto, "jobId", jobId);

        UserEntity user = sampleUser();
        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .id(101L)
                .jobId(jobId)
                .mode(AnalyzeMode.QUICK_1VIEW)
                .status(AnalyzeJobStatus.COMPLETED)
                .measurementModel("quick")
                .gender("male")
                .resultJson("""
                        {
                          "success": true,
                          "lengths": {
                            "shoulder_width_cm": 41.82,
                            "arm_length_cm": 56.74,
                            "leg_length_cm": 81.19,
                            "torso_length_cm": 49.31,
                            "inseam_cm": null
                          },
                          "circumferences": null,
                          "body_shape": null
                        }
                        """)
                .user(user)
                .build();

        when(userRepository.findByUsernameForUpdate(username)).thenReturn(Optional.of(user));
        when(analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)).thenReturn(Optional.of(job));
        when(historyRepository.findByAnalyzeJob_Id(job.getId())).thenReturn(Optional.empty());
        when(historyRepository.findTopByUser_IdOrderByUserSeqDesc(user.getId())).thenReturn(Optional.empty());
        when(openAiFashionClient.recommend(any(JsonNode.class), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(objectMapper.readTree("{\"version\":\"mvp.v1\"}"));

        FashionRecommendationResponseDTO response = service.recommend(username, dto);

        assertEquals(jobId, response.jobId());
        assertEquals("quick", response.measurementModel());
        assertEquals("mvp.v1", response.recommendation().path("version").asText());

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(openAiFashionClient).recommend(captor.capture(), eq("quick"), eq("male"), isNull(), isNull());
        JsonNode input = captor.getValue();
        assertTrue(input.path("success").asBoolean());
        assertEquals("male", input.path("gender").asText());
        assertTrue(input.path("lengths").isObject());
        assertTrue(input.path("circumferences").isMissingNode());
        assertTrue(input.path("body_shape").isMissingNode());
        verify(historyRepository, times(1)).save(any(MeasurementRecommendationHistoryEntity.class));
    }

    @Test
    void premiumModelSendsSuccessLengthsCircumferencesAndBodyShapeToOpenAi() throws Exception {
        String username = "otp_user";
        String jobId = "job-premium-1";
        FashionRecommendationRequestDTO dto = new FashionRecommendationRequestDTO();
        ReflectionTestUtils.setField(dto, "jobId", jobId);

        UserEntity user = sampleUser();
        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .id(202L)
                .jobId(jobId)
                .mode(AnalyzeMode.STANDARD_2VIEW)
                .status(AnalyzeJobStatus.COMPLETED)
                .measurementModel("premium")
                .gender("female")
                .resultJson("""
                        {
                          "success": true,
                          "lengths": {
                            "shoulder_width_cm": 44.45,
                            "arm_length_cm": 58.27,
                            "leg_length_cm": 82.6,
                            "torso_length_cm": 45.32,
                            "inseam_cm": 78.92
                          },
                          "circumferences": {
                            "chest_cm": 84.76,
                            "waist_cm": 68.39,
                            "hip_cm": 94.79,
                            "thigh_cm": 45.23,
                            "chest_axis_m": 1.123,
                            "waist_axis_m": 1.09,
                            "hip_axis_m": 0.786,
                            "thigh_axis_m": 0.715
                          },
                          "body_shape": "pear"
                        }
                        """)
                .user(user)
                .build();

        when(userRepository.findByUsernameForUpdate(username)).thenReturn(Optional.of(user));
        when(analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)).thenReturn(Optional.of(job));
        when(historyRepository.findByAnalyzeJob_Id(job.getId())).thenReturn(Optional.empty());
        when(historyRepository.findTopByUser_IdOrderByUserSeqDesc(user.getId())).thenReturn(Optional.empty());
        when(openAiFashionClient.recommend(any(JsonNode.class), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(objectMapper.readTree("{\"version\":\"mvp.v1\"}"));

        service.recommend(username, dto);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(openAiFashionClient).recommend(captor.capture(), eq("premium"), eq("female"), isNull(), isNull());
        JsonNode input = captor.getValue();
        assertTrue(input.path("success").asBoolean());
        assertEquals("female", input.path("gender").asText());
        assertTrue(input.path("lengths").isObject());
        assertTrue(input.path("circumferences").isObject());
        assertEquals("pear", input.path("body_shape").asText());
        verify(historyRepository, times(1)).save(any(MeasurementRecommendationHistoryEntity.class));
    }

    @Test
    void forwardsLanguageAndLocationHintsToOpenAi() throws Exception {
        String username = "otp_user";
        String jobId = "job-hint-1";
        FashionRecommendationRequestDTO dto = new FashionRecommendationRequestDTO();
        ReflectionTestUtils.setField(dto, "jobId", jobId);
        ReflectionTestUtils.setField(dto, "language", "ko-KR");
        ReflectionTestUtils.setField(dto, "location", "ko-KR, Asia/Seoul");

        UserEntity user = sampleUser();
        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .id(909L)
                .jobId(jobId)
                .mode(AnalyzeMode.STANDARD_2VIEW)
                .status(AnalyzeJobStatus.COMPLETED)
                .measurementModel("premium")
                .gender("female")
                .resultJson("""
                        {
                          "success": true,
                          "lengths": {
                            "shoulder_width_cm": 42.10,
                            "arm_length_cm": 57.20,
                            "leg_length_cm": 80.60,
                            "torso_length_cm": 46.10,
                            "inseam_cm": 76.80
                          },
                          "circumferences": {
                            "chest_cm": 90.0,
                            "waist_cm": 75.0,
                            "hip_cm": 92.0,
                            "thigh_cm": 50.0
                          },
                          "body_shape": "balanced"
                        }
                        """)
                .user(user)
                .build();

        when(userRepository.findByUsernameForUpdate(username)).thenReturn(Optional.of(user));
        when(analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)).thenReturn(Optional.of(job));
        when(historyRepository.findByAnalyzeJob_Id(job.getId())).thenReturn(Optional.empty());
        when(historyRepository.findTopByUser_IdOrderByUserSeqDesc(user.getId())).thenReturn(Optional.empty());
        when(openAiFashionClient.recommend(any(JsonNode.class), anyString(), anyString(), any(), any()))
                .thenReturn(objectMapper.readTree("{\"version\":\"mvp.v1\"}"));

        service.recommend(username, dto);

        verify(openAiFashionClient).recommend(
                any(JsonNode.class),
                eq("premium"),
                eq("female"),
                eq("ko-KR"),
                eq("ko-KR, Asia/Seoul")
        );
    }

    @Test
    void sameJobReturnsSavedHistoryWithoutNewOpenAiCall() throws Exception {
        String username = "otp_user";
        String jobId = "job-premium-2";
        FashionRecommendationRequestDTO dto = new FashionRecommendationRequestDTO();
        ReflectionTestUtils.setField(dto, "jobId", jobId);

        UserEntity user = sampleUser();
        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .id(303L)
                .jobId(jobId)
                .mode(AnalyzeMode.STANDARD_2VIEW)
                .status(AnalyzeJobStatus.COMPLETED)
                .measurementModel("premium")
                .resultJson("{\"success\":true,\"lengths\":{\"shoulder_width_cm\":44.45}}")
                .user(user)
                .build();
        MeasurementRecommendationHistoryEntity history = MeasurementRecommendationHistoryEntity.builder()
                .id(1L)
                .user(user)
                .userSeq(7L)
                .analyzeJob(job)
                .mode(AnalyzeMode.STANDARD_2VIEW)
                .measurementModel("premium")
                .frontImageKey("front")
                .sideImageKey("side")
                .glbObjectKey("glb")
                .resultJson("{\"success\":true,\"lengths\":{\"shoulder_width_cm\":44.45}}")
                .llmResponseJson("{\"version\":\"mvp.v1\",\"sources\":[]}")
                .llmModel("gpt-4.1-mini")
                .promptVersion("mvp.v1")
                .build();

        when(userRepository.findByUsernameForUpdate(username)).thenReturn(Optional.of(user));
        when(analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)).thenReturn(Optional.of(job));
        when(historyRepository.findByAnalyzeJob_Id(job.getId())).thenReturn(Optional.of(history));

        FashionRecommendationResponseDTO response = service.recommend(username, dto);

        assertEquals(jobId, response.jobId());
        assertEquals("premium", response.measurementModel());
        assertEquals("mvp.v1", response.recommendation().path("version").asText());
        verify(openAiFashionClient, never()).recommend(any(JsonNode.class), anyString(), anyString(), any(), any());
        verify(historyRepository, never()).save(any(MeasurementRecommendationHistoryEntity.class));
    }

    @Test
    void throwsWhenAnalyzeJobNotCompleted() {
        String username = "otp_user";
        String jobId = "job-running-1";
        FashionRecommendationRequestDTO dto = new FashionRecommendationRequestDTO();
        ReflectionTestUtils.setField(dto, "jobId", jobId);

        UserEntity user = sampleUser();
        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .id(404L)
                .jobId(jobId)
                .status(AnalyzeJobStatus.RUNNING)
                .mode(AnalyzeMode.STANDARD_2VIEW)
                .user(user)
                .build();

        when(userRepository.findByUsernameForUpdate(username)).thenReturn(Optional.of(user));
        when(analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)).thenReturn(Optional.of(job));
        when(historyRepository.findByAnalyzeJob_Id(job.getId())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.recommend(username, dto));
        assertTrue(ex.getMessage().contains("완료된 job"));
    }

    private UserEntity sampleUser() {
        return UserEntity.builder()
                .id(1L)
                .username("otp_user")
                .socialProviderType(SocialProviderType.EMAIL_OTP)
                .providerUserId("otp_user@example.com")
                .roleType(UserRoleType.USER)
                .quickTicketBalance(1)
                .premiumTicketBalance(1)
                .email("otp_user@example.com")
                .isLock(false)
                .build();
    }
}
