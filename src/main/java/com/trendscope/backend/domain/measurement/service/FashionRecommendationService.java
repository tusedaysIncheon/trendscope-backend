package com.trendscope.backend.domain.measurement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trendscope.backend.domain.analyze.entity.AnalyzeJobEntity;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import com.trendscope.backend.domain.analyze.repository.AnalyzeJobRepository;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationHistoryDetailResponseDTO;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationHistoryItemDTO;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationHistoryListResponseDTO;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationRequestDTO;
import com.trendscope.backend.domain.measurement.dto.FashionRecommendationResponseDTO;
import com.trendscope.backend.domain.measurement.entity.MeasurementRecommendationHistoryEntity;
import com.trendscope.backend.domain.measurement.repository.MeasurementRecommendationHistoryRepository;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FashionRecommendationService {

    private static final int MAX_HISTORY_SIZE = 100;

    private final AnalyzeJobRepository analyzeJobRepository;
    private final UserRepository userRepository;
    private final MeasurementRecommendationHistoryRepository historyRepository;
    private final OpenAiFashionClient openAiFashionClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public FashionRecommendationResponseDTO recommend(String username, FashionRecommendationRequestDTO dto) {
        UserEntity lockedUser = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        AnalyzeJobEntity job = analyzeJobRepository.findByJobIdAndUserUsername(dto.getJobId(), username)
                .orElseThrow(() -> new IllegalArgumentException("측정 job을 찾을 수 없습니다."));

        MeasurementRecommendationHistoryEntity existing = historyRepository.findByAnalyzeJob_Id(job.getId()).orElse(null);
        if (existing != null) {
            return toRecommendationResponse(existing);
        }

        if (job.getStatus() != AnalyzeJobStatus.COMPLETED) {
            throw new IllegalArgumentException("측정이 완료된 job만 추천 생성이 가능합니다.");
        }
        if (!hasText(job.getResultJson())) {
            throw new IllegalArgumentException("측정 결과 JSON이 비어 있습니다.");
        }

        JsonNode rawResult;
        try {
            rawResult = objectMapper.readTree(job.getResultJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("저장된 측정 결과 JSON 파싱에 실패했습니다.", e);
        }
        if (!rawResult.path("success").asBoolean(false)) {
            throw new IllegalArgumentException("측정 실패 결과에서는 추천을 생성할 수 없습니다.");
        }

        String measurementModel = inferMeasurementModel(job);
        JsonNode aiInput = buildAiInput(rawResult, measurementModel);
        JsonNode recommendation = openAiFashionClient.recommend(
                aiInput,
                measurementModel,
                dto.getLanguage(),
                dto.getLocation()
        );

        Long nextUserSeq = historyRepository.findTopByUser_IdOrderByUserSeqDesc(lockedUser.getId())
                .map(item -> item.getUserSeq() + 1L)
                .orElse(1L);

        MeasurementRecommendationHistoryEntity history = MeasurementRecommendationHistoryEntity.builder()
                .user(lockedUser)
                .userSeq(nextUserSeq)
                .analyzeJob(job)
                .mode(job.getMode())
                .measurementModel(measurementModel)
                .frontImageKey(job.getFrontImageKey())
                .sideImageKey(job.getSideImageKey())
                .glbObjectKey(job.getGlbObjectKey())
                .resultJson(rawResult.toString())
                .llmResponseJson(recommendation.toString())
                .llmModel(openAiFashionClient.modelName())
                .promptVersion(openAiFashionClient.promptVersion())
                .build();
        historyRepository.save(history);
        return toRecommendationResponse(history);
    }

    @Transactional(readOnly = true)
    public FashionRecommendationHistoryListResponseDTO getHistory(String username, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
        List<FashionRecommendationHistoryItemDTO> histories = historyRepository
                .findByUser_UsernameOrderByCreatedDateDesc(username, PageRequest.of(0, safeSize))
                .stream()
                .map(this::toHistoryItem)
                .toList();
        return new FashionRecommendationHistoryListResponseDTO(username, histories);
    }

    @Transactional(readOnly = true)
    public FashionRecommendationHistoryDetailResponseDTO getHistoryDetail(String username, Long userSeq) {
        if (userSeq == null || userSeq < 1L) {
            throw new IllegalArgumentException("userSeq는 1 이상의 값이어야 합니다.");
        }
        MeasurementRecommendationHistoryEntity history = historyRepository
                .findByUser_UsernameAndUserSeq(username, userSeq)
                .orElseThrow(() -> new IllegalArgumentException("해당 추천 이력을 찾을 수 없습니다."));
        return toHistoryDetail(history);
    }

    private JsonNode buildAiInput(JsonNode rawResult, String measurementModel) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("success", true);

        JsonNode lengths = rawResult.path("lengths");
        if (!lengths.isObject()) {
            throw new IllegalArgumentException("측정 결과에 lengths 필드가 없습니다.");
        }
        input.set("lengths", lengths);

        if ("premium".equals(measurementModel)) {
            JsonNode circumferences = rawResult.path("circumferences");
            if (!circumferences.isObject()) {
                throw new IllegalArgumentException("premium 결과에 circumferences 필드가 없습니다.");
            }
            input.set("circumferences", circumferences);

            JsonNode bodyShape = rawResult.path("body_shape");
            if (bodyShape.isMissingNode() || bodyShape.isNull() || !hasText(bodyShape.asText(""))) {
                input.put("body_shape", "unknown");
            } else {
                input.put("body_shape", bodyShape.asText());
            }
        }

        return input;
    }

    private String inferMeasurementModel(AnalyzeJobEntity job) {
        if (hasText(job.getMeasurementModel())) {
            return job.getMeasurementModel().trim().toLowerCase();
        }
        return job.getMode() == AnalyzeMode.QUICK_1VIEW ? "quick" : "premium";
    }

    private FashionRecommendationResponseDTO toRecommendationResponse(MeasurementRecommendationHistoryEntity history) {
        return new FashionRecommendationResponseDTO(
                history.getAnalyzeJob().getJobId(),
                history.getMeasurementModel(),
                parseJson(history.getLlmResponseJson(), "llmResponseJson")
        );
    }

    private FashionRecommendationHistoryItemDTO toHistoryItem(MeasurementRecommendationHistoryEntity history) {
        return new FashionRecommendationHistoryItemDTO(
                history.getUserSeq(),
                history.getAnalyzeJob().getJobId(),
                history.getMode(),
                history.getMeasurementModel(),
                history.getFrontImageKey(),
                history.getSideImageKey(),
                history.getGlbObjectKey(),
                history.getCreatedDate()
        );
    }

    private FashionRecommendationHistoryDetailResponseDTO toHistoryDetail(MeasurementRecommendationHistoryEntity history) {
        return new FashionRecommendationHistoryDetailResponseDTO(
                history.getUserSeq(),
                history.getAnalyzeJob().getJobId(),
                history.getMode(),
                history.getMeasurementModel(),
                history.getFrontImageKey(),
                history.getSideImageKey(),
                history.getGlbObjectKey(),
                parseJson(history.getResultJson(), "resultJson"),
                parseJson(history.getLlmResponseJson(), "llmResponseJson"),
                history.getLlmModel(),
                history.getPromptVersion(),
                history.getCreatedDate()
        );
    }

    private JsonNode parseJson(String json, String fieldName) {
        if (!hasText(json)) {
            throw new IllegalArgumentException(fieldName + " 값이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " 파싱에 실패했습니다.", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
