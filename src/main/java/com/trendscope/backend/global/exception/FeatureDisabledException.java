package com.trendscope.backend.global.exception;

public class FeatureDisabledException extends RuntimeException {
    public FeatureDisabledException(String message) {
        super(message);
    }
}
