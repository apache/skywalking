package com.ai.cloud.skywalking.agent.test;

import com.ai.skywalking.testframework.api.TraceTreeAssert;
import org.junit.After;
import org.junit.Test;

public class RedisClusterPluginTest {

    @After
    public void clearData(){
        TraceTreeAssert.clearTraceData();
    }


    @Test
    public void testSetData() {
        RedisClusterOperator.setData("key1", "value1");
        TraceTreeAssert.assertEquals(new String[][]{
                // 根据实际情况进行修改
                {"0.0", "127.0.0.1:7001 set", "key=key1"},
                {"0", "127.0.0.1:7001;127.0.0.1:7000; set", "key=key1"},
        });

    }

    @Test
    public void testGetData() {
        RedisClusterOperator.getData("key1");
        TraceTreeAssert.assertEquals(new String[][]{
                // 根据实际情况进行修改
                {"0.0", "127.0.0.1:7001 get", "key=key1"},
                {"0", "127.0.0.1:7001;127.0.0.1:7000; get", "key=key1"},
        });
    }


    @Test
    public void testDelData() {
        RedisClusterOperator.delData("key1");
        TraceTreeAssert.assertEquals(new String[][]{
                // 根据实际情况进行修改
                {"0.0", "127.0.0.1:7001 del", "key=key1"},
                {"0", "127.0.0.1:7001;127.0.0.1:7000; del", "key=key1"},
        });
    }
}
