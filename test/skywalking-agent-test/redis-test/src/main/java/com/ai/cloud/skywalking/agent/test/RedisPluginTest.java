package com.ai.cloud.skywalking.agent.test;

import test.com.ai.skywalking.reflect.api.TraceTreeAssert;

public class RedisPluginTest {
    public static void main(String[] args) {
        RedisOperator.setData("key1", "value1");
        TraceTreeAssert.assertEquals(new String[][]{
                {null, "0", "127.0.0.1:6379 set", "key=key1"},
        });
    }
}
