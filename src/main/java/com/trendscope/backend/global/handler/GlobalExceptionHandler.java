package com.trendscope.backend.global.handler;

import com.trendscope.backend.global.exception.InvalidTokenException;
import com.trendscope.backend.global.exception.FeatureDisabledException;
import com.trendscope.backend.global.exception.UpstreamServiceException;
import com.trendscope.backend.global.util.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.rmi.AccessException;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(
            IllegalArgumentException e,
            HttpServletRequest request) {
        log.warn("잘못된 요청 (Bad Request) - IP: {}, URL: {}, Message: {}", request.getRemoteAddr(),
                request.getRequestURI(), e.getMessage());
        return ApiResponse.fail("BAD_REQUEST", e.getMessage());
    }

    // 503
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(FeatureDisabledException.class)
    public ApiResponse<Void> handleFeatureDisabledException(FeatureDisabledException e) {
        log.warn("비활성화 기능 요청: {}", e.getMessage());
        return ApiResponse.fail("FEATURE_DISABLED", e.getMessage());
    }

    // 502 Upstream dependency failure (e.g. Modal endpoint down)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(UpstreamServiceException.class)
    public ApiResponse<Void> handleUpstreamServiceException(UpstreamServiceException e) {
        log.warn("업스트림 호출 실패 code={} status={} message={}", e.getErrorCode(), e.getUpstreamStatus(), e.getMessage());
        return ApiResponse.fail(e.getErrorCode(), e.getMessage());
    }

    // 403
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessException.class)
    public ApiResponse<Void> handleAccessException(
            AccessException e) {
        log.warn(e.getMessage());
        return ApiResponse.fail("FORBIDDEN", "접근 권한이 없습니다.");
    }

    // 500
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(
            Exception e,
            HttpServletRequest request) {
        log.error("서버 내부 치명적 오류! URL: {}", request.getRequestURI(), e);
        return ApiResponse.fail("INTERNAL_SERVER_ERROR", "서버 오류");
    }

    // 400 Validation
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Map<String, String>> handleValidationExeption(MethodArgumentNotValidException e) {
        String errorMessage = "잘못된 요청입니다.";
        if (e.getBindingResult().hasErrors()) {
            String specificMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();

            if (specificMessage != null && !specificMessage.isBlank()) {
                errorMessage = specificMessage;
            }
        }

        // Validation 오류의 경우 data 필드에 상세 내용을 넣을지, message에 넣을지 결정해야 하는데,
        // 기존 로직은 message에 넣고 반환했음.
        // ApiResponse 구조상 message에 에러 메시지를 넣는 것이 적절함.
        // data는 null 또는 추가 정보.

        return ApiResponse.fail("BAD_REQUEST", errorMessage);
    }

    // 400 Validation (multipart form/model attribute binding)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException e) {
        String errorMessage = "잘못된 요청입니다.";
        if (e.getBindingResult().hasErrors()) {
            String specificMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
            if (specificMessage != null && !specificMessage.isBlank()) {
                errorMessage = specificMessage;
            }
        }
        return ApiResponse.fail("BAD_REQUEST", errorMessage);
    }

    // 400 Missing multipart file/part
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ApiResponse<Void> handleMissingServletRequestPartException(MissingServletRequestPartException e) {
        return ApiResponse.fail("BAD_REQUEST", e.getRequestPartName() + " 파트가 필요합니다.");
    }

    // 413 File too large
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        long maxSize = e.getMaxUploadSize();
        String limitText = maxSize > 0 ? String.format("%.1fMB", maxSize / (1024.0 * 1024.0)) : "설정된 제한";
        return ApiResponse.fail("PAYLOAD_TOO_LARGE", "업로드 파일 용량이 너무 큽니다. 최대 허용 용량: " + limitText);
    }

    // 401
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(InvalidTokenException.class)
    public ApiResponse<Void> handleInvalidTokenException(InvalidTokenException e) {
        // 로그 남기기 (경고 레벨)
        log.warn("인증 실패: {}", e.getMessage());

        // 프론트엔드에게 "UNAUTHORIZED" 코드와 예외 메시지("로그인이 필요합니다") 전달
        return ApiResponse.fail("UNAUTHORIZED", e.getMessage());
    }

}
