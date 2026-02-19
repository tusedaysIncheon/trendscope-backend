package com.trendscope.backend.domain.analyze.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trendscope.backend.domain.analyze.dto.AnalyzeOneShotRequestDTO;
import com.trendscope.backend.domain.analyze.dto.AnalyzeOneShotResponseDTO;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import com.trendscope.backend.global.util.S3Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyzeOneShotServiceContractTest {

    @Mock
    private S3Util s3Util;

    @Mock
    private ModalAnalyzeClient modalAnalyzeClient;

    private AnalyzeOneShotService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AnalyzeOneShotService(s3Util, modalAnalyzeClient);
        ReflectionTestUtils.setField(service, "oneShotEnabled", true);
        ReflectionTestUtils.setField(service, "oneShotS3Prefix", "analyze-one-shot");
        ReflectionTestUtils.setField(service, "downloadUrlExpireMinutes", 30L);

        lenient().when(s3Util.createObjectKey(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class) + "/" + inv.getArgument(1, String.class));
        lenient().doNothing().when(s3Util).uploadMultipartFile(anyString(), any(MultipartFile.class));
        lenient().when(s3Util.createPresignedGetUrl(anyString(), any(Duration.class)))
                .thenAnswer(inv -> "https://get.local/" + inv.getArgument(0, String.class));
        lenient().when(s3Util.createPresignedPutUrl(anyString(), any(Duration.class)))
                .thenAnswer(inv -> "https://put.local/" + inv.getArgument(0, String.class));
        ObjectNode ok = objectMapper.createObjectNode().put("success", true);
        lenient().when(modalAnalyzeClient.analyze(anyMap())).thenReturn(ok);
    }

    @Test
    void quickModePayloadUsesQuickModelAndPhotoPose() {
        AnalyzeOneShotRequestDTO dto = buildRequest(AnalyzeMode.QUICK_1VIEW, "quick", false);

        AnalyzeOneShotResponseDTO response = service.analyze(dto);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(modalAnalyzeClient).analyze(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();

        assertEquals("QUICK_1VIEW", payload.get("mode"));
        assertEquals("quick", payload.get("measurement_model"));
        assertEquals("PHOTO_POSE", payload.get("output_pose"));
        assertEquals("fast", payload.get("quality_mode"));
        assertEquals(false, payload.get("normalize_with_anny"));
        assertNull(payload.get("side_image_url"));
        assertEquals(AnalyzeMode.QUICK_1VIEW, response.getMode());
    }

    @Test
    void premiumModePayloadUsesPremiumModelAndPhotoPose() {
        AnalyzeOneShotRequestDTO dto = buildRequest(AnalyzeMode.STANDARD_2VIEW, "premium", true);

        AnalyzeOneShotResponseDTO response = service.analyze(dto);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(modalAnalyzeClient).analyze(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();

        assertEquals("STANDARD_2VIEW", payload.get("mode"));
        assertEquals("premium", payload.get("measurement_model"));
        assertEquals("PHOTO_POSE", payload.get("output_pose"));
        assertEquals("accurate", payload.get("quality_mode"));
        assertEquals(true, payload.get("normalize_with_anny"));
        assertNotNull(payload.get("side_image_url"));
        assertEquals(AnalyzeMode.STANDARD_2VIEW, response.getMode());
    }

    @Test
    void rejectsQuickMeasurementModelForStandardMode() {
        AnalyzeOneShotRequestDTO dto = buildRequest(AnalyzeMode.STANDARD_2VIEW, "quick", true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.analyze(dto));
        assertTrue(ex.getMessage().contains("measurementModel=quick"));
        verify(modalAnalyzeClient, never()).analyze(anyMap());
    }

    @Test
    void rejectsPremiumMeasurementModelForQuickMode() {
        AnalyzeOneShotRequestDTO dto = buildRequest(AnalyzeMode.QUICK_1VIEW, "premium", false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.analyze(dto));
        assertTrue(ex.getMessage().contains("measurementModel=premium"));
        verify(modalAnalyzeClient, never()).analyze(anyMap());
    }

    private AnalyzeOneShotRequestDTO buildRequest(AnalyzeMode mode, String measurementModel, boolean includeSide) {
        AnalyzeOneShotRequestDTO dto = new AnalyzeOneShotRequestDTO();
        ReflectionTestUtils.setField(dto, "mode", mode);
        ReflectionTestUtils.setField(
                dto,
                "frontImage",
                new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", "front".getBytes(StandardCharsets.UTF_8))
        );
        if (includeSide) {
            ReflectionTestUtils.setField(
                    dto,
                    "sideImage",
                    new MockMultipartFile("sideImage", "side.jpg", "image/jpeg", "side".getBytes(StandardCharsets.UTF_8))
            );
        }
        ReflectionTestUtils.setField(dto, "heightCm", 175.0d);
        ReflectionTestUtils.setField(dto, "weightKg", 70.0d);
        ReflectionTestUtils.setField(dto, "gender", "male");
        ReflectionTestUtils.setField(dto, "measurementModel", measurementModel);
        return dto;
    }
}
