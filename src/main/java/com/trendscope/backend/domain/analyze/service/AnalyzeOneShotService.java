package com.trendscope.backend.domain.analyze.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.trendscope.backend.domain.analyze.dto.AnalyzeOneShotRequestDTO;
import com.trendscope.backend.domain.analyze.dto.AnalyzeOneShotResponseDTO;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import com.trendscope.backend.global.exception.FeatureDisabledException;
import com.trendscope.backend.global.util.S3Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeOneShotService {

    private final S3Util s3Util;
    private final ModalAnalyzeClient modalAnalyzeClient;

    @Value("${app.analyze.download-url-expire-minutes:30}")
    private long downloadUrlExpireMinutes;

    @Value("${app.analyze.one-shot.enabled:false}")
    private boolean oneShotEnabled;

    @Value("${app.analyze.one-shot.s3-prefix:analyze-one-shot}")
    private String oneShotS3Prefix;

    public AnalyzeOneShotResponseDTO analyze(AnalyzeOneShotRequestDTO dto) {
        if (!oneShotEnabled) {
            throw new FeatureDisabledException("one-shot API가 비활성화되어 있습니다. ANALYZE_ONE_SHOT_ENABLED=true로 활성화하세요.");
        }
        if (dto.getMode() == AnalyzeMode.STANDARD_2VIEW && (dto.getSideImage() == null || dto.getSideImage().isEmpty())) {
            throw new IllegalArgumentException("STANDARD_2VIEW 모드는 sideImage가 필요합니다.");
        }
        if (dto.getFrontImage() == null || dto.getFrontImage().isEmpty()) {
            throw new IllegalArgumentException("frontImage는 필수입니다.");
        }

        String gender = normalizeGender(dto.getGender());
        String measurementModel = normalizeMeasurementModel(dto.getMeasurementModel(), dto.getMode());
        String qualityMode = "premium".equals(measurementModel) ? "accurate" : "fast";
        boolean normalizeWithAnny = "premium".equals(measurementModel);
        String outputPose = "PHOTO_POSE";

        String requestId = UUID.randomUUID().toString().replace("-", "");
        String prefix = oneShotS3Prefix + "/" + requestId;

        String frontKey = s3Util.createObjectKey(prefix + "/input", safeFilename(dto.getFrontImage(), "front.jpg"));
        String sideKey = dto.getMode() == AnalyzeMode.STANDARD_2VIEW
                ? s3Util.createObjectKey(prefix + "/input", safeFilename(dto.getSideImage(), "side.jpg"))
                : null;
        String glbKey = prefix + "/output/body.glb";
        String debugGlbKey = prefix + "/output/debug_joints.glb";

        s3Util.uploadMultipartFile(frontKey, dto.getFrontImage());
        if (sideKey != null && dto.getSideImage() != null && !dto.getSideImage().isEmpty()) {
            s3Util.uploadMultipartFile(sideKey, dto.getSideImage());
        }

        Duration expiry = Duration.ofMinutes(Math.max(1, downloadUrlExpireMinutes));
        String frontImageUrl = s3Util.createPresignedGetUrl(frontKey, expiry);
        String sideImageUrl = sideKey == null ? null : s3Util.createPresignedGetUrl(sideKey, expiry);
        String glbUploadUrl = s3Util.createPresignedPutUrl(glbKey, expiry);
        String debugGlbUploadUrl = s3Util.createPresignedPutUrl(debugGlbKey, expiry);

        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", dto.getMode().name());
        payload.put("measurement_model", measurementModel);
        payload.put("front_image_url", frontImageUrl);
        payload.put("side_image_url", sideImageUrl);
        payload.put("glb_upload_url", glbUploadUrl);
        payload.put("debug_glb_upload_url", debugGlbUploadUrl);
        payload.put("height_cm", dto.getHeightCm());
        payload.put("weight_kg", dto.getWeightKg());
        payload.put("gender", gender);
        payload.put("job_id", requestId);
        payload.put("quality_mode", qualityMode);
        payload.put("normalize_with_anny", normalizeWithAnny);
        payload.put("output_pose", outputPose);

        log.info("one-shot analyze start requestId={} mode={}", requestId, dto.getMode());
        JsonNode modalResponse = modalAnalyzeClient.analyze(payload);
        boolean success = modalResponse.path("success").asBoolean(false);
        String glbDownloadUrl = success ? s3Util.createPresignedGetUrl(glbKey, expiry) : null;
        String debugGlbDownloadUrl = success ? s3Util.createPresignedGetUrl(debugGlbKey, expiry) : null;
        log.info("one-shot analyze finished requestId={} success={}", requestId, success);

        return new AnalyzeOneShotResponseDTO(
                requestId,
                dto.getMode(),
                frontKey,
                sideKey,
                glbKey,
                glbDownloadUrl,
                debugGlbKey,
                debugGlbDownloadUrl,
                modalResponse
        );
    }

    private String normalizeGender(String gender) {
        if (!hasText(gender)) {
            throw new IllegalArgumentException("gender는 필수입니다.");
        }
        String normalized = gender.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("male", "female", "other").contains(normalized)) {
            throw new IllegalArgumentException("gender는 male/female/other 중 하나여야 합니다.");
        }
        return normalized;
    }

    private String normalizeMeasurementModel(String measurementModel, AnalyzeMode mode) {
        if (!hasText(measurementModel)) {
            return mode == AnalyzeMode.QUICK_1VIEW ? "quick" : "premium";
        }
        String normalized = measurementModel.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("quick", "premium").contains(normalized)) {
            throw new IllegalArgumentException("measurementModel은 quick/premium 중 하나여야 합니다.");
        }
        if ("quick".equals(normalized) && mode != AnalyzeMode.QUICK_1VIEW) {
            throw new IllegalArgumentException("measurementModel=quick 은 mode=QUICK_1VIEW에서만 사용할 수 있습니다.");
        }
        if ("premium".equals(normalized) && mode != AnalyzeMode.STANDARD_2VIEW) {
            throw new IllegalArgumentException("measurementModel=premium 은 mode=STANDARD_2VIEW에서만 사용할 수 있습니다.");
        }
        return normalized;
    }

    private String safeFilename(MultipartFile file, String fallback) {
        if (file == null || !hasText(file.getOriginalFilename())) {
            return fallback;
        }
        return file.getOriginalFilename();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
