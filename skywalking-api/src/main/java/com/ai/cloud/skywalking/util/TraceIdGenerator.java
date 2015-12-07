package com.ai.cloud.skywalking.util;

import java.util.UUID;

public final class TraceIdGenerator {
    private TraceIdGenerator() {
        // Non
    }

    public static String generate() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
