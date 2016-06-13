package com.ai.cloud.skywalking.agent.test;

import com.ai.skywalking.testframework.api.TraceTreeAssert;
import org.junit.After;
import org.junit.Test;

public class RedisPluginTest {

    @After
    public void clearData(){
        TraceTreeAssert.clearTraceData();
    }

    @Test
    public void testSetData() {
        RedisOperator.setData("key1", "value1");
        TraceTreeAssert.assertEquals(new String[][]{
                {"0", "127.0.0.1:6379 set", "key=key1"},
        });
    }

    @Test
    public void testGetData() {
        RedisOperator.getData("key1");
        TraceTreeAssert.assertEquals(new String[][]{
                {"0", "127.0.0.1:6379 get", "key=key1"},
        });
    }


    @Test
    public void testDelData() {
        RedisOperator.delData("key1");
        TraceTreeAssert.assertEquals(new String[][]{
                {"0", "127.0.0.1:6379 del", "key=key1"},
        });
    }
}
