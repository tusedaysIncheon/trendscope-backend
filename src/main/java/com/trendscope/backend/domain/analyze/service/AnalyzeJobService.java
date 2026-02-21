package com.trendscope.backend.domain.analyze.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendscope.backend.domain.analyze.dto.*;
import com.trendscope.backend.domain.analyze.entity.AnalyzeJobEntity;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import com.trendscope.backend.domain.user.dto.TicketTransactionResponseDTO;
import com.trendscope.backend.domain.analyze.repository.AnalyzeJobRepository;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import com.trendscope.backend.domain.user.service.TicketLedgerService;
import com.trendscope.backend.global.exception.UpstreamServiceException;
import com.trendscope.backend.global.util.S3Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeJobService {

    private static final int MAX_LIST_SIZE = 50;

    private final AnalyzeJobRepository analyzeJobRepository;
    private final UserRepository userRepository;
    private final TicketLedgerService ticketLedgerService;
    private final S3Util s3Util;
    private final ModalAnalyzeClient modalAnalyzeClient;
    private final AnalyzeShareTokenService analyzeShareTokenService;
    private final ObjectMapper objectMapper;

    @Value("${app.analyze.upload-url-expire-minutes:10}")
    private long uploadUrlExpireMinutes;

    @Value("${app.analyze.download-url-expire-minutes:30}")
    private long downloadUrlExpireMinutes;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Transactional
    public AnalyzeUploadUrlsResponseDTO issueUploadUrls(String username, AnalyzeUploadUrlsRequestDTO dto) {
        UserEntity user = userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        AnalyzeMode mode = dto.getMode();
        if (mode == AnalyzeMode.STANDARD_2VIEW && !hasText(dto.getSideFilename())) {
            throw new IllegalArgumentException("STANDARD_2VIEW 모드는 sideFilename이 필요합니다.");
        }

        String jobId = UUID.randomUUID().toString().replace("-", "");
        String prefix = "analyze/" + user.getUsername() + "/" + jobId;

        String frontKey = s3Util.createObjectKey(prefix + "/input", dto.getFrontFilename());
        String sideKey = mode == AnalyzeMode.STANDARD_2VIEW
                ? s3Util.createObjectKey(prefix + "/input", dto.getSideFilename())
                : null;
        String glbKey = prefix + "/output/body.glb";

        Duration uploadExpiry = Duration.ofMinutes(Math.max(1, uploadUrlExpireMinutes));
        String frontPutUrl = s3Util.createPresignedPutUrl(frontKey, uploadExpiry);
        String sidePutUrl = sideKey == null ? null : s3Util.createPresignedPutUrl(sideKey, uploadExpiry);
        String glbPutUrl = s3Util.createPresignedPutUrl(glbKey, uploadExpiry);

        AnalyzeJobEntity job = AnalyzeJobEntity.builder()
                .jobId(jobId)
                .user(user)
                .mode(mode)
                .status(AnalyzeJobStatus.QUEUED)
                .frontImageKey(frontKey)
                .sideImageKey(sideKey)
                .glbObjectKey(glbKey)
                .normalizeWithAnny(true)
                .measurementModel(inferMeasurementModelByMode(mode))
                .queuedAt(LocalDateTime.now())
                .build();
        analyzeJobRepository.save(job);

        return new AnalyzeUploadUrlsResponseDTO(
                jobId,
                mode,
                job.getStatus(),
                new AnalyzePresignedUploadDTO(frontKey, frontPutUrl),
                sideKey == null ? null : new AnalyzePresignedUploadDTO(sideKey, sidePutUrl),
                new AnalyzePresignedUploadDTO(glbKey, glbPutUrl)
        );
    }

    @Transactional
    public AnalyzeJobStartResponseDTO start(String username, String jobId, AnalyzeJobStartRequestDTO dto) {
        AnalyzeJobEntity job = analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)
                .orElseThrow(() -> new IllegalArgumentException("측정 job을 찾을 수 없습니다."));

        if (job.getStatus() == AnalyzeJobStatus.RUNNING) {
            throw new IllegalArgumentException("이미 실행 중인 job 입니다.");
        }
        if (job.getMode() == AnalyzeMode.STANDARD_2VIEW && !hasText(job.getSideImageKey())) {
            throw new IllegalArgumentException("STANDARD_2VIEW 모드는 측면 이미지가 필요합니다.");
        }

        String gender = normalizeGender(dto.getGender());
        String measurementModel = normalizeMeasurementModel(dto.getMeasurementModel(), job.getMode());
        String qualityMode = inferQualityModeByMeasurementModel(measurementModel);
        boolean normalizeWithAnny = "premium".equals(measurementModel);
        String outputPose = "PHOTO_POSE";
        TicketType requiredTicketType = inferRequiredTicketType(measurementModel);
        ticketLedgerService.holdForAnalyze(username, requiredTicketType, job.getJobId());

        job.setInputProfile(
                dto.getHeightCm(),
                dto.getWeightKg(),
                gender,
                qualityMode,
                normalizeWithAnny,
                measurementModel,
                outputPose
        );
        job.markQueued();
        analyzeJobRepository.save(job);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> processJob(jobId));
                }
            });
        } else {
            CompletableFuture.runAsync(() -> processJob(jobId));
        }

        return new AnalyzeJobStartResponseDTO(
                job.getJobId(),
                job.getMode(),
                job.getStatus(),
                job.getQueuedAt()
        );
    }

    @Transactional(readOnly = true)
    public AnalyzeJobStatusResponseDTO getJob(String username, String jobId) {
        AnalyzeJobEntity job = analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)
                .orElseThrow(() -> new IllegalArgumentException("측정 job을 찾을 수 없습니다."));
        return toStatusResponse(job);
    }

    @Transactional(readOnly = true)
    public AnalyzeJobShareResponseDTO issueShareLink(String username, String jobId) {
        AnalyzeJobEntity job = analyzeJobRepository.findByJobIdAndUserUsername(jobId, username)
                .orElseThrow(() -> new IllegalArgumentException("측정 job을 찾을 수 없습니다."));

        if (job.getStatus() != AnalyzeJobStatus.COMPLETED || !hasText(job.getResultJson())) {
            throw new IllegalArgumentException("완료된 측정 결과만 공유할 수 있습니다.");
        }

        AnalyzeShareTokenService.IssuedShareToken issuedToken = analyzeShareTokenService.issueToken(job.getJobId());
        String shareUrl = buildShareUrl(issuedToken.token());
        return new AnalyzeJobShareResponseDTO(issuedToken.token(), shareUrl, issuedToken.expiresAt());
    }

    @Transactional(readOnly = true)
    public AnalyzeSharedJobResponseDTO getSharedJob(String token) {
        String jobId = analyzeShareTokenService.extractJobId(token);
        AnalyzeJobEntity job = analyzeJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("공유된 측정 결과를 찾을 수 없습니다."));

        if (job.getStatus() != AnalyzeJobStatus.COMPLETED || !hasText(job.getResultJson())) {
            throw new IllegalArgumentException("공유 가능한 측정 결과가 아닙니다.");
        }

        JsonNode result;
        try {
            result = objectMapper.readTree(job.getResultJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("공유 결과 데이터가 손상되었습니다.");
        }

        Duration downloadExpiry = Duration.ofMinutes(Math.max(1, downloadUrlExpireMinutes));
        String glbDownloadUrl = hasText(job.getGlbObjectKey())
                ? s3Util.createPresignedGetUrl(job.getGlbObjectKey(), downloadExpiry)
                : null;

        return new AnalyzeSharedJobResponseDTO(
                job.getJobId(),
                job.getMode(),
                job.getStatus(),
                glbDownloadUrl,
                job.getHeightCm(),
                job.getWeightKg(),
                job.getGender(),
                job.getMeasurementModel(),
                result,
                job.getCompletedAt(),
                job.getCreatedDate()
        );
    }

    @Transactional(readOnly = true)
    public AnalyzeJobListResponseDTO getMyJobs(String username, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_LIST_SIZE));
        List<AnalyzeJobListItemDTO> jobs = analyzeJobRepository
                .findByUserUsernameOrderByCreatedDateDesc(username, PageRequest.of(0, safeSize))
                .stream()
                .map(job -> new AnalyzeJobListItemDTO(
                        job.getJobId(),
                        job.getMode(),
                        job.getStatus(),
                        job.getGlbObjectKey(),
                        job.getCreatedDate(),
                        job.getCompletedAt()
                ))
                .toList();
        return new AnalyzeJobListResponseDTO(username, jobs);
    }

    @Transactional
    public long deleteCompletedJobsBefore(LocalDateTime cutoff) {
        return analyzeJobRepository.deleteByCompletedAtBefore(cutoff);
    }

    private void processJob(String jobId) {
        try {
            AnalyzeJobEntity job = analyzeJobRepository.findByJobId(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("측정 job을 찾을 수 없습니다."));

            job.markRunning();
            analyzeJobRepository.save(job);

            Map<String, Object> payload = buildModalPayload(job);
            JsonNode modalResponse = modalAnalyzeClient.analyze(payload);

            boolean success = modalResponse.path("success").asBoolean(false);
            if (success) {
                job.markCompleted(modalResponse.toString());
                analyzeJobRepository.save(job);
                consumeHeldTicket(job);
                return;
            }

            String errorCode = text(modalResponse, "error");
            if (!hasText(errorCode)) {
                errorCode = "analyze_failed";
            }
            String detail = text(modalResponse, "detail");
            if (!hasText(detail)) {
                detail = modalResponse.toString();
            }
            job.markFailed(errorCode, detail);
            analyzeJobRepository.save(job);
            releaseHeldTicket(job);
        } catch (UpstreamServiceException e) {
            String errorCode = hasText(e.getErrorCode()) ? e.getErrorCode() : "modal_call_failed";
            String detail = safeErrorDetail(e);
            log.error("측정 job 업스트림 호출 실패. jobId={} errorCode={}", jobId, errorCode, e);
            analyzeJobRepository.findByJobId(jobId).ifPresent(job -> {
                job.markFailed(errorCode, detail);
                analyzeJobRepository.save(job);
                releaseHeldTicket(job);
            });
        } catch (Exception e) {
            log.error("측정 job 처리 실패. jobId={}", jobId, e);
            analyzeJobRepository.findByJobId(jobId).ifPresent(job -> {
                job.markFailed("modal_call_failed", safeErrorDetail(e));
                analyzeJobRepository.save(job);
                releaseHeldTicket(job);
            });
        }
    }

    private Map<String, Object> buildModalPayload(AnalyzeJobEntity job) {
        Duration downloadExpiry = Duration.ofMinutes(Math.max(1, downloadUrlExpireMinutes));
        String frontImageUrl = s3Util.createPresignedGetUrl(job.getFrontImageKey(), downloadExpiry);
        String sideImageUrl = hasText(job.getSideImageKey())
                ? s3Util.createPresignedGetUrl(job.getSideImageKey(), downloadExpiry)
                : null;
        String glbUploadUrl = s3Util.createPresignedPutUrl(job.getGlbObjectKey(), downloadExpiry);
        String measurementModel = hasText(job.getMeasurementModel())
                ? job.getMeasurementModel()
                : inferMeasurementModelByMode(job.getMode());

        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", job.getMode().name());
        payload.put("measurement_model", measurementModel);
        payload.put("front_image_url", frontImageUrl);
        payload.put("side_image_url", sideImageUrl);
        payload.put("glb_upload_url", glbUploadUrl);
        payload.put("height_cm", job.getHeightCm());
        payload.put("weight_kg", job.getWeightKg());
        payload.put("gender", job.getGender());
        payload.put("job_id", job.getJobId());
        payload.put("quality_mode", job.getQualityMode());
        payload.put("normalize_with_anny", job.getNormalizeWithAnny());
        payload.put("output_pose", "PHOTO_POSE");
        return payload;
    }

    private AnalyzeJobStatusResponseDTO toStatusResponse(AnalyzeJobEntity job) {
        JsonNode result = null;
        if (hasText(job.getResultJson())) {
            try {
                result = objectMapper.readTree(job.getResultJson());
            } catch (Exception e) {
                log.warn("job result json 파싱 실패. jobId={}", job.getJobId());
            }
        }
        String glbDownloadUrl = null;
        if (job.getStatus() == AnalyzeJobStatus.COMPLETED && hasText(job.getGlbObjectKey())) {
            Duration downloadExpiry = Duration.ofMinutes(Math.max(1, downloadUrlExpireMinutes));
            glbDownloadUrl = s3Util.createPresignedGetUrl(job.getGlbObjectKey(), downloadExpiry);
        }

        return new AnalyzeJobStatusResponseDTO(
                job.getJobId(),
                job.getMode(),
                job.getStatus(),
                job.getFrontImageKey(),
                job.getSideImageKey(),
                job.getGlbObjectKey(),
                glbDownloadUrl,
                job.getHeightCm(),
                job.getWeightKg(),
                job.getGender(),
                job.getQualityMode(),
                job.getNormalizeWithAnny(),
                job.getMeasurementModel(),
                job.getOutputPose(),
                job.getErrorCode(),
                job.getErrorDetail(),
                result,
                job.getQueuedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getCreatedDate(),
                job.getUpdatedDate()
        );
    }

    private String buildShareUrl(String token) {
        String baseUrl = hasText(frontendBaseUrl) ? frontendBaseUrl.trim() : "http://localhost:5173";
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return baseUrl + "/share/result/" + encodedToken;
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
            return inferMeasurementModelByMode(mode);
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

    private String inferMeasurementModelByMode(AnalyzeMode mode) {
        return mode == AnalyzeMode.QUICK_1VIEW ? "quick" : "premium";
    }

    private String inferQualityModeByMeasurementModel(String measurementModel) {
        return "premium".equals(measurementModel) ? "accurate" : "fast";
    }

    private TicketType inferRequiredTicketType(String measurementModel) {
        return "quick".equals(measurementModel) ? TicketType.QUICK : TicketType.PREMIUM;
    }

    private TicketType inferRequiredTicketType(AnalyzeJobEntity job) {
        String measurementModel = hasText(job.getMeasurementModel())
                ? job.getMeasurementModel().trim().toLowerCase(Locale.ROOT)
                : inferMeasurementModelByMode(job.getMode());
        return inferRequiredTicketType(measurementModel);
    }

    private void consumeHeldTicket(AnalyzeJobEntity job) {
        try {
            TicketType ticketType = inferRequiredTicketType(job);
            String username = resolveUsername(job);
            TicketTransactionResponseDTO response = ticketLedgerService.consumeHeldForAnalyze(
                    username,
                    ticketType,
                    job.getJobId()
            );
            log.info(
                    "측정 티켓 확정 차감 처리. jobId={} ticketType={} applied={} delta={} totalBalance={}",
                    job.getJobId(),
                    response.ticketType(),
                    response.applied(),
                    response.delta(),
                    response.totalTicketBalance()
            );
        } catch (Exception e) {
            throw new IllegalStateException("측정 성공 후 티켓 확정 차감(CONSUME) 처리에 실패했습니다.", e);
        }
    }

    private void releaseHeldTicket(AnalyzeJobEntity job) {
        try {
            TicketType ticketType = inferRequiredTicketType(job);
            String username = resolveUsername(job);
            TicketTransactionResponseDTO response = ticketLedgerService.releaseHeldForAnalyze(
                    username,
                    ticketType,
                    job.getJobId()
            );
            log.info(
                    "측정 실패 티켓 해제 처리. jobId={} ticketType={} applied={} delta={} totalBalance={}",
                    job.getJobId(),
                    response.ticketType(),
                    response.applied(),
                    response.delta(),
                    response.totalTicketBalance()
            );
        } catch (Exception e) {
            log.warn("측정 실패 티켓 해제(RELEASE) 처리 실패. jobId={} reason={}", job.getJobId(), e.getMessage());
        }
    }

    private String resolveUsername(AnalyzeJobEntity job) {
        Long userId = Optional.ofNullable(job.getUser())
                .map(UserEntity::getId)
                .orElseThrow(() -> new IllegalStateException("측정 job 사용자 식별자(userId)를 찾을 수 없습니다."));

        return userRepository.findById(userId)
                .map(UserEntity::getUsername)
                .orElseThrow(() -> new IllegalStateException("측정 job 사용자를 찾을 수 없습니다. userId=" + userId));
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

    private String safeErrorDetail(Throwable throwable) {
        if (throwable == null) {
            return "unknown_error";
        }
        if (hasText(throwable.getMessage())) {
            return throwable.getMessage();
        }
        return throwable.getClass().getSimpleName();
    }
}
