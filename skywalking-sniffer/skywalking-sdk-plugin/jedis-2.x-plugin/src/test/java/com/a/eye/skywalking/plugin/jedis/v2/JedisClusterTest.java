package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.TracingBootstrap;
import com.a.eye.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class JedisClusterTest {
    public void test() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            PluginException {
        TracingBootstrap.main(new String[] {"com.a.eye.skywalking.plugin.jedis.v2.JedisClusterTest"});
    }

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException {
        JedisCluster jedisCluster = new JedisCluster(getHostAndPorts());
        jedisCluster.set("11111", "111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
        RequestSpanAssert.assertEquals(new String[][] {
                // 根据实际情况进行修改
                {"0.0", "127.0.0.1:7001 set", "key=11111"}, {"0", "127.0.0.1:7002;127.0.0.1:7001;127.0.0.1:7000;127.0.0.1:7005;127.0.0.1:7004;127.0.0.1:7003; set", "key=11111"},});

    }

    private static Set<HostAndPort> getHostAndPorts() {
        Set<HostAndPort> redisEnv = new HashSet<HostAndPort>();
        redisEnv.add(new HostAndPort("127.0.0.1", 7000));
        redisEnv.add(new HostAndPort("127.0.0.1", 7001));
        redisEnv.add(new HostAndPort("127.0.0.1", 7002));
        redisEnv.add(new HostAndPort("127.0.0.1", 7003));
        redisEnv.add(new HostAndPort("127.0.0.1", 7004));
        redisEnv.add(new HostAndPort("127.0.0.1", 7005));
        return redisEnv;
    }
}
