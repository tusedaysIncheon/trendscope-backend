package com.trendscope.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class TrendscopeBackendApplicationTests {

    @Test
    void applicationClassLoads() {
        assertDoesNotThrow(() -> Class.forName("com.trendscope.backend.TrendscopeBackendApplication"));
    }

}
