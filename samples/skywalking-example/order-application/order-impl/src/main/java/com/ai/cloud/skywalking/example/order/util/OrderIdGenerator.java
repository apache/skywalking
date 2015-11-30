package com.ai.cloud.skywalking.example.order.util;

import java.util.UUID;

public class OrderIdGenerator {
    public static String generate() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
