package com.trendscope.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TrendscopeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrendscopeBackendApplication.class, args);
    }

}
