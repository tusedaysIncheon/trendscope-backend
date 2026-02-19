package com.trendscope.backend.domain.analyze.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendscope.backend.global.exception.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModalAnalyzeClient {

    private final ObjectMapper objectMapper;

    @Value("${modal.base-url:}")
    private String modalBaseUrl;

    @Value("${modal.analyze-path:/analyze-body}")
    private String analyzePath;

    @Value("${modal.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${modal.read-timeout-ms:600000}")
    private int readTimeoutMs;

    public JsonNode analyze(Map<String, Object> payload) {
        if (!hasText(modalBaseUrl)) {
            throw new IllegalArgumentException("modal.base-url가 설정되지 않았습니다.");
        }

        RestClient client = buildClient();

        try {
            ResponseEntity<String> response = postForEntity(client, analyzePath, payload);

            if (response.getStatusCode().is3xxRedirection()) {
                String location = response.getHeaders().getFirst("Location");
                log.warn(
                        "Modal analyze 리다이렉트 수신 status={} location={} path={}",
                        response.getStatusCode(),
                        location,
                        analyzePath
                );
                if (!hasText(location)) {
                    throw new UpstreamServiceException(
                            "MODAL_REDIRECT_NO_LOCATION",
                            "Modal analyze 리다이렉트 응답에 Location 헤더가 없습니다.",
                            response.getStatusCode().value(),
                            null
                    );
                }
                // 303 See Other must be followed with GET.
                response = getForEntity(client, location);
            }

            String rawResponse = response.getBody();
            if (!hasText(rawResponse)) {
                log.warn("Modal analyze 빈 응답 수신 status={} path={}", response.getStatusCode(), analyzePath);
                throw new UpstreamServiceException(
                        "MODAL_EMPTY_RESPONSE",
                        "Modal analyze 응답 본문이 비어 있습니다. status=" + response.getStatusCode(),
                        response.getStatusCode().value(),
                        null
                );
            }
            return objectMapper.readTree(rawResponse);
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.warn("Modal analyze 호출 실패 status={} body={}", e.getStatusCode(), body);
            int statusCode = e.getStatusCode().value();
            if (statusCode == 404 && containsStoppedAppSignal(body)) {
                throw new UpstreamServiceException(
                        "MODAL_ENDPOINT_STOPPED",
                        "Modal endpoint가 중지 상태입니다. modal serve/deploy 후 다시 시도하세요.",
                        statusCode,
                        e
                );
            }
            throw new UpstreamServiceException(
                    "MODAL_CALL_FAILED",
                    "Modal analyze 호출 실패: " + e.getStatusCode(),
                    statusCode,
                    e
            );
        } catch (Exception e) {
            String rootCause = rootCauseMessage(e);
            log.warn("Modal analyze 호출 중 오류 발생. rootCause={}", rootCause, e);
            throw new UpstreamServiceException(
                    "MODAL_CALL_EXCEPTION",
                    "Modal analyze 호출 중 오류가 발생했습니다. cause=" + rootCause,
                    0,
                    e
            );
        }
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1000, readTimeoutMs));
        return RestClient.builder()
                .baseUrl(modalBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private ResponseEntity<String> postForEntity(RestClient client, String uriOrPath, Map<String, Object> payload) {
        if (uriOrPath == null) {
            return client.post()
                    .uri(analyzePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
        }
        URI uri = URI.create(uriOrPath);
        if (uri.isAbsolute()) {
            return client.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
        }
        return client.post()
                .uri(uriOrPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> getForEntity(RestClient client, String uriOrPath) {
        if (uriOrPath == null) {
            return client.get()
                    .uri(analyzePath)
                    .retrieve()
                    .toEntity(String.class);
        }
        URI uri = URI.create(uriOrPath);
        if (uri.isAbsolute()) {
            return client.get()
                    .uri(uri)
                    .retrieve()
                    .toEntity(String.class);
        }
        return client.get()
                .uri(uriOrPath)
                .retrieve()
                .toEntity(String.class);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        if (hasText(cursor.getMessage())) {
            return cursor.getMessage();
        }
        return cursor.getClass().getSimpleName();
    }

    private boolean containsStoppedAppSignal(String responseBody) {
        if (!hasText(responseBody)) {
            return false;
        }
        return responseBody.toLowerCase().contains("app for invoked web endpoint is stopped");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
