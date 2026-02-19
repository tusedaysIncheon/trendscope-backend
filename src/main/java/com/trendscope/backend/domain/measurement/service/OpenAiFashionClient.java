package com.trendscope.backend.domain.measurement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendscope.backend.global.exception.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiFashionClient {

    private static final String PROMPT_VERSION = "mvp.v4";
    private static final int MAX_LANGUAGE_ENFORCE_ATTEMPTS = 2;
    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣]");
    private static final Pattern LATIN_PATTERN = Pattern.compile("[A-Za-z]");
    private static final Pattern HIRAGANA_KATAKANA_PATTERN = Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}]");
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\p{IsHan}]");
    private static final Set<String> NON_NARRATIVE_KEYS = Set.of(
            "version",
            "url",
            "platform",
            "product_name",
            "type",
            "rise"
    );

    private static final String PREMIUM_SYSTEM_PROMPT = """
            너는 대한민국 상위 0.1% 남성 패션 스타일리스트다.
            패션 매거진 에디터 출신이며 수천명의 스타일링을 담당했다.
            사용자는 빠르게 바로 입을 수 있는 코디/구매 가이드를 원한다.

            규칙:
            - 입력은 신체정보 JSON이며 모든 수치는 cm 단위다.
            - JSON 안의 값만 근거로 판단한다.
            - 평균 대비 단정 표현 금지. 대신 비율상 ~해 보일 수 있음 / 이렇게 연출하면 보정됨 형태로 서술한다.
            - 체형 비하/불안 유발 표현 금지.
            - 전문적이되 친절한 톤 유지.
            - 반드시 수치 계산 근거를 포함한다.
            - 둘레/체중 추정 금지.
            - 결과는 지정된 JSON 스키마로만 출력한다.
            - 마크다운, 코드블록, 추가 텍스트 절대 금지.
            - sources는 항상 마지막 필드로 출력한다.
            - sources에는 실제로 존재하고, 직접 접속 가능한 URL만 넣는다.
            - 존재하지 않는 자료, 지어낸 자료명, 접속 불가/깨진 링크는 절대로 절대로 sources에 넣지 않는다.
            - 검증 가능한 출처가 없으면 sources는 반드시 빈 배열([])로 출력한다.
            - example_products.url은 플랫폼 + product_name 검색 링크를 넣는다.

            아래 JSON 스키마 구조로만 응답하고 필드 누락 금지:
            {
              "version": "mvp.v1",
              "input_summary": {
                "shoulder_width_cm": 0,
                "arm_length_cm": 0,
                "leg_length_cm": 0,
                "torso_length_cm": 0,
                "inseam_cm": 0
              },
              "calculations": {
                "leg_to_torso_ratio": 0,
                "threshold_long_leg": 0,
                "ratio_result": ""
              },
              "diagnosis": {
                "upper_lower_balance": {
                  "analysis": "",
                  "style_direction": ""
                },
                "shoulder_frame": {
                  "analysis": "",
                  "style_direction": ""
                },
                "arm_balance": {
                  "analysis": "",
                  "style_direction": ""
                }
              },
              "strategy": {
                "top_length": {
                  "recommendation": "",
                  "wearing_method": [],
                  "reason": ""
                },
                "bottom_fit": {
                  "rise": "mid | high",
                  "length": "",
                  "silhouette": "",
                  "reason": ""
                },
                "shoulder_correction": {
                  "neckline": [],
                  "shoulder_line": [],
                  "reason": ""
                }
              },
              "outfit_guide": [
                {
                  "title": "",
                  "items": [],
                  "fit_notes": []
                },
                {
                  "title": "",
                  "items": [],
                  "fit_notes": []
                },
                {
                  "title": "",
                  "items": [],
                  "fit_notes": []
                }
              ],
              "key_items": [
                {
                  "name": "",
                  "spec": [],
                  "why": "",
                  "avoid": [],
                  "example_products": [
                    {
                      "platform": "",
                      "product_name": "",
                      "url": ""
                    },
                    {
                      "platform": "",
                      "product_name": "",
                      "url": ""
                    },
                    {
                      "platform": "",
                      "product_name": "",
                      "url": ""
                    }
                  ]
                }
              ],
              "sources": [
                {
                  "name": "",
                  "url": "",
                  "type": "webzine | lookbook | brand"
                }
              ]
            }
            """;

    private static final String QUICK_SYSTEM_PROMPT = """
            너는 대한민국 상위 0.1% 남성 패션 스타일리스트다.
            패션 매거진 에디터 출신이며 수천명의 스타일링을 담당했다.
            사용자는 빠르게 바로 입을 수 있는 코디/구매 가이드를 원한다.

            규칙:
            - 입력은 신체정보 JSON이며 모든 수치는 cm 단위다.
            - JSON 안의 값만 근거로 판단한다.
            - 평균 대비 단정 표현 금지. 대신 비율상 ~해 보일 수 있음 / 이렇게 연출하면 보정됨 형태로 서술한다.
            - 체형 비하/불안 유발 표현 금지.
            - 전문적이되 친절한 톤 유지.
            - 반드시 수치 계산 근거를 포함한다.
            - 둘레/체중 추정 금지.
            - 결과는 지정된 JSON 스키마로만 출력한다.
            - 마크다운, 코드블록, 추가 텍스트 절대 금지.
            - sources는 항상 마지막 필드로 출력한다.
            - sources에는 실제로 존재하고, 직접 접속 가능한 URL만 넣는다.
            - 존재하지 않는 자료, 지어낸 자료명, 접속 불가/깨진 링크는 절대로 절대로 sources에 넣지 않는다.
            - 검증 가능한 출처가 없으면 sources는 반드시 빈 배열([])로 출력한다.

            QUICK 모드 출력 스키마:
            {
              "version": "mvp.v1",
              "input_summary": {
                "shoulder_width_cm": 0,
                "arm_length_cm": 0,
                "leg_length_cm": 0,
                "torso_length_cm": 0,
                "inseam_cm": null
              },
              "calculations": {
                "leg_to_torso_ratio": 0,
                "threshold_long_leg": 0,
                "ratio_result": ""
              },
              "diagnosis": {
                "upper_lower_balance": {
                  "analysis": "",
                  "style_direction": ""
                },
                "shoulder_frame": {
                  "analysis": "",
                  "style_direction": ""
                },
                "arm_balance": {
                  "analysis": "",
                  "style_direction": ""
                }
              },
              "strategy": {},
              "outfit_guide": [],
              "key_items": [],
              "sources": [
                {
                  "name": "",
                  "url": "",
                  "type": "webzine"
                }
              ]
            }
            """;

    private final ObjectMapper objectMapper;

    @Value("${openai.base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.chat-completions-path:/v1/chat/completions}")
    private String chatCompletionsPath;

    @Value("${openai.model:gpt-4.1-mini}")
    private String openAiModel;

    @Value("${openai.timeout-ms:60000}")
    private int timeoutMs;

    public String modelName() {
        return openAiModel;
    }

    public String promptVersion() {
        return PROMPT_VERSION;
    }

    public JsonNode recommend(
            JsonNode measurementInput,
            String measurementModel,
            String preferredLanguage,
            String locationHint
    ) {
        if (!hasText(openAiApiKey)) {
            throw new IllegalArgumentException("openai.api-key가 설정되지 않았습니다.");
        }

        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(measurementInput);
        } catch (Exception e) {
            throw new IllegalArgumentException("OpenAI 입력 JSON 직렬화에 실패했습니다.", e);
        }

        String safeLocation = sanitizeLocationHint(locationHint);
        String targetLanguage = resolveResponseLanguage(preferredLanguage, safeLocation);

        JsonNode recommendation = null;
        for (int attempt = 1; attempt <= MAX_LANGUAGE_ENFORCE_ATTEMPTS; attempt++) {
            boolean strictRetry = attempt > 1;
            recommendation = requestRecommendation(
                    inputJson,
                    measurementModel,
                    targetLanguage,
                    safeLocation,
                    strictRetry
            );
            if (isResponseLanguageAcceptable(recommendation, targetLanguage)) {
                return recommendation;
            }
            log.warn(
                    "OpenAI 응답 언어 불일치 감지 attempt={} targetLanguage={} measurementModel={}",
                    attempt,
                    targetLanguage,
                    measurementModel
            );
        }
        throw new UpstreamServiceException(
                "OPENAI_LANGUAGE_MISMATCH",
                "OpenAI 응답 언어가 요청 언어와 일치하지 않습니다.",
                0
        );
    }

    private JsonNode requestRecommendation(
            String inputJson,
            String measurementModel,
            String targetLanguage,
            String locationHint,
            boolean strictRetry
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", openAiModel);
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", buildMessages(inputJson, measurementModel, targetLanguage, locationHint, strictRetry));

        RestClient client = buildClient();
        try {
            ResponseEntity<String> response = client.post()
                    .uri(chatCompletionsPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            String raw = response.getBody();
            if (!hasText(raw)) {
                throw new UpstreamServiceException(
                        "OPENAI_EMPTY_RESPONSE",
                        "OpenAI 응답 본문이 비어 있습니다.",
                        response.getStatusCode().value()
                );
            }

            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!hasText(content)) {
                throw new UpstreamServiceException(
                        "OPENAI_INVALID_RESPONSE",
                        "OpenAI 응답에서 content를 찾을 수 없습니다.",
                        response.getStatusCode().value()
                );
            }

            String sanitized = stripCodeFence(content);
            return objectMapper.readTree(sanitized);
        } catch (RestClientResponseException e) {
            log.warn("OpenAI 호출 실패 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new UpstreamServiceException(
                    "OPENAI_CALL_FAILED",
                    "OpenAI 호출 실패: " + e.getStatusCode(),
                    e.getStatusCode().value(),
                    e
            );
        } catch (UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamServiceException(
                    "OPENAI_CALL_EXCEPTION",
                    "OpenAI 응답 처리 중 오류가 발생했습니다.",
                    0,
                    e
            );
        }
    }

    private List<Map<String, String>> buildMessages(
            String inputJson,
            String measurementModel,
            String targetLanguage,
            String locationHint,
            boolean strictRetry
    ) {
        StringBuilder userContent = new StringBuilder("Input JSON:\n")
                .append(inputJson)
                .append("\n\nTarget language code: ")
                .append(targetLanguage);
        if (hasText(locationHint)) {
            userContent.append("\nLocation hint: ").append(locationHint);
        }
        return List.of(
                Map.of("role", "system", "content", systemPromptByMeasurementModel(measurementModel)),
                Map.of("role", "system", "content", localizationSystemPrompt(targetLanguage, locationHint, strictRetry)),
                Map.of("role", "user", "content", userContent.toString())
        );
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int safeTimeout = Math.max(1000, timeoutMs);
        requestFactory.setConnectTimeout(safeTimeout);
        requestFactory.setReadTimeout(safeTimeout);
        return RestClient.builder()
                .baseUrl(openAiBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String noStart = trimmed.replaceFirst("^```(?:json)?\\s*", "");
        return noStart.replaceFirst("\\s*```$", "").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String systemPromptByMeasurementModel(String measurementModel) {
        if ("quick".equalsIgnoreCase(measurementModel)) {
            return QUICK_SYSTEM_PROMPT;
        }
        return PREMIUM_SYSTEM_PROMPT;
    }

    private String localizationSystemPrompt(String languageCode, String locationHint, boolean strictRetry) {
        String safeLocation = compact(locationHint, 120);
        String locationRule = hasText(safeLocation)
                ? "- 사용자 location 힌트: \"" + safeLocation + "\"\n"
                + "  플랫폼 선택/예시 검색 링크(example_products.url) 우선순위를 이 location에 맞춰라."
                : "- 사용자 location 힌트가 없으면 대한민국 사용성 기준 플랫폼을 우선 고려하라.";
        String retryRule = strictRetry
                ? "- 직전 응답이 언어 규칙을 위반했다. 이번에는 서술형 텍스트를 반드시 %s로만 다시 작성하라."
                .formatted(languageName(languageCode))
                : "";

        return """
                출력 언어 규칙(최우선):
                - 응답 JSON의 키 이름은 절대 번역하지 않는다.
                - 응답 JSON의 값 중 서술형 텍스트는 반드시 %s 로만 작성한다.
                - 언어를 섞지 않는다.
                %s
                %s
                """.formatted(languageName(languageCode), locationRule, retryRule);
    }

    private String resolveResponseLanguage(String preferredLanguage, String locationHint) {
        String byPreferred = normalizeLanguageCode(preferredLanguage);
        if (hasText(byPreferred)) {
            return byPreferred;
        }
        String byLocation = normalizeLanguageCode(locationHint);
        if (hasText(byLocation)) {
            return byLocation;
        }
        return "ko";
    }

    private String normalizeLanguageCode(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("ko")
                || value.contains("korean")
                || value.contains("한국어")
                || value.contains("대한민국")
                || value.contains("korea")
                || value.contains("seoul")) {
            return "ko";
        }
        if (value.startsWith("en")
                || value.contains("english")
                || value.contains("united states")
                || value.contains("america")
                || value.contains("united kingdom")
                || value.contains("london")) {
            return "en";
        }
        if (value.startsWith("ja")
                || value.contains("japanese")
                || value.contains("일본어")
                || value.contains("japan")
                || value.contains("tokyo")) {
            return "ja";
        }
        if (value.startsWith("zh")
                || value.contains("chinese")
                || value.contains("중국어")
                || value.contains("china")
                || value.contains("taiwan")
                || value.contains("hong kong")
                || value.contains("beijing")
                || value.contains("shanghai")) {
            return "zh";
        }
        return null;
    }

    private String languageName(String languageCode) {
        return switch (languageCode) {
            case "en" -> "English";
            case "ja" -> "Japanese";
            case "zh" -> "Chinese";
            default -> "Korean";
        };
    }

    private String compact(String value, int maxLength) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String sanitizeLocationHint(String value) {
        if (!hasText(value)) {
            return "";
        }
        return compact(value.replaceAll("[\\r\\n\\t]+", " "), 120);
    }

    private boolean isResponseLanguageAcceptable(JsonNode recommendation, String languageCode) {
        List<String> narratives = new ArrayList<>();
        collectNarrativeText(recommendation, "", narratives);
        if (narratives.isEmpty()) {
            return true;
        }
        String merged = String.join(" ", narratives);
        return switch (languageCode) {
            case "en" -> LATIN_PATTERN.matcher(merged).find();
            case "ja" -> HIRAGANA_KATAKANA_PATTERN.matcher(merged).find()
                    || CJK_PATTERN.matcher(merged).find();
            case "zh" -> CJK_PATTERN.matcher(merged).find();
            default -> HANGUL_PATTERN.matcher(merged).find();
        };
    }

    private void collectNarrativeText(JsonNode node, String key, List<String> sink) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectNarrativeText(entry.getValue(), entry.getKey(), sink));
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectNarrativeText(item, key, sink));
            return;
        }
        if (!node.isTextual()) {
            return;
        }

        String text = node.asText("").trim();
        if (!hasText(text)) {
            return;
        }
        if (NON_NARRATIVE_KEYS.contains(key)) {
            return;
        }
        if (looksLikeUrl(text)) {
            return;
        }
        sink.add(text);
    }

    private boolean looksLikeUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("www.");
    }
}
