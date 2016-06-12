package com.ai.cloud.skywalking.jedis.v2.plugin;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import org.junit.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class JedisClusterTest {
    @Test
    public void test() throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException,
            SecurityException, ClassNotFoundException {
        TracingBootstrap
                .main(new String[]{"com.ai.cloud.skywalking.jedis.v2.plugin.JedisClusterTest"});
    }

    public static void main(String[] args) throws ClassNotFoundException,
            SQLException, InterruptedException {
        JedisCluster jedisCluster = null;
        try {
            jedisCluster = new JedisCluster(getHostAndPorts());
            long start = System.currentTimeMillis();
            jedisCluster.set("11111", "111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
            long end = System.currentTimeMillis();
            System.out.println(end - start + "ms");
            jedisCluster.del("11111");
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public void testNormal() throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException, InterruptedException {
        JedisClusterTest.main(null);
    }
}
