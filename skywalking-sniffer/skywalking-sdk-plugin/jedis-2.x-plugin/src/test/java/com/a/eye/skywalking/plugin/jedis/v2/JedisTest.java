package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.TracingBootstrap;
import com.a.eye.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

public class JedisTest {
    public void test() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            PluginException {
        TracingBootstrap.main(new String[] {"JedisTest"});
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
