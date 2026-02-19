package com.trendscope.backend.global.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        return Map.of("ok", true, "service", "trendscope-backend");
    }
}
