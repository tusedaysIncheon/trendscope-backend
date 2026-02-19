package com.trendscope.backend.global.exception;

import lombok.Getter;

@Getter
public class UpstreamServiceException extends RuntimeException {

    private final String errorCode;
    private final int upstreamStatus;

    public UpstreamServiceException(String errorCode, String message, int upstreamStatus) {
        super(message);
        this.errorCode = errorCode;
        this.upstreamStatus = upstreamStatus;
    }

    public UpstreamServiceException(String errorCode, String message, int upstreamStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.upstreamStatus = upstreamStatus;
    }
}
