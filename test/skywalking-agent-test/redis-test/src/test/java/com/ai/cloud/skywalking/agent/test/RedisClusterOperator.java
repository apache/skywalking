package com.ai.cloud.skywalking.agent.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Created by xin on 16-6-13.
 */
public class RedisClusterOperator {

    private static Logger logger = LogManager.getLogger(RedisClusterOperator.class);
    private static JedisCluster jedisCluster = null;

    static {
        InputStream inputStream = RedisOperator.class.getResourceAsStream("/redis.conf");
        Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            logger.error("Failed to load redis.conf", e);
            System.exit(-1);
        }
        jedisCluster = new JedisCluster(getHostAndPorts(properties.getProperty("redis.cluster.host", "127.0.0.1:7000")));
    }

    private static Set<HostAndPort> getHostAndPorts(String property) {
        Set<HostAndPort> redisEnv = new HashSet<HostAndPort>();
        String[] hosts = property.split(";");
        for (String host : hosts) {
            String[] hostAndPort = host.split(":");
            redisEnv.add(new HostAndPort(hostAndPort[0], Integer.parseInt(hostAndPort[1])));
        }
        return redisEnv;
    }


    public static void setData(String key, String value) {
        jedisCluster.set(key, value);
    }

    public static String getData(String key) {
        return jedisCluster.get(key);
    }

    public static void delData(String key) {
        jedisCluster.del(key);
    }

}
