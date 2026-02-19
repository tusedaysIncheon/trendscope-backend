package com.trendscope.backend.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamsConfig {

    // Key Convention: 도메인:리소스:용도
    public static final String BOAT_STREAM_KEY = "boat:stream:log";
    public static final String BOAT_CONSUMER_GROUP = "boat-group";

}