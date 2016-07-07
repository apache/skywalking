package com.ai.cloud.skywalking.jedis.v2.plugin;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import com.ai.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

public class JedisTest {
    @Test
    public void test() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
        TracingBootstrap.main(new String[] {"com.ai.cloud.skywalking.jedis.v2.plugin.JedisTest"});
    }

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException {
        Jedis jedis = null;
        try {
            jedis = new Jedis("127.0.0.1", 6379);
            jedis.set("11111", "111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
            RequestSpanAssert.assertEquals(new String[][] {{"0", "127.0.0.1:6379 set", "key=11111"},});
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jedis.close();
        }
    }

    public void testNormal() throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException, InterruptedException {
        JedisTest.main(null);
    }
}
