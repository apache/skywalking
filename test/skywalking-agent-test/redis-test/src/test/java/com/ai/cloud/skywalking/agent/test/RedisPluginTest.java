package com.ai.cloud.skywalking.agent.test;


import com.ai.skywalking.testframework.api.RequestSpanAssert;
import org.junit.After;
import org.junit.Test;

public class RedisPluginTest {

    @After
    public void clearData() {
        RequestSpanAssert.clearTraceData();
    }

    @Test
    public void testSetData() throws Exception {
        RedisOperator.setData("key1", "value1");
        RequestSpanAssert.assertEquals(new String[][] {{"0", "127.0.0.1:6379 set", "key=key1"},});
    }

    @Test
    public void testGetData() throws Exception {
        RedisOperator.getData("key1");
        RequestSpanAssert.assertEquals(new String[][] {{"0", "127.0.0.1:6379 get", "key=key1"},});
    }


    @Test
    public void testDelData() throws Exception {
        RedisOperator.delData("key1");
        RequestSpanAssert.assertEquals(new String[][] {{"0", "127.0.0.1:6379 del", "key=key1"},});
    }
}
