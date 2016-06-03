package com.ai.cloud.skywalking.agent.test.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.InputStream;
import java.util.Properties;

public class JedisUtils {
    private static JedisPool jedisPool;
    private static Logger logger = LogManager.getLogger(JedisUtils.class);

    static {
        InputStream redisConfigFileStream = JedisUtils.class.getResourceAsStream("/redis.conf");
        Properties jedisConfig = new Properties();
        try {
            jedisConfig.load(redisConfigFileStream);
        } catch (Exception e) {
            System.err.print("Failed to load redis.conf");
            System.exit(-1);
        }

        jedisPool = new JedisPool(jedisConfig.getProperty("redis.ip", "127.0.0.1"),
                Integer.parseInt(jedisConfig.getProperty("redis.port", "6379")));
    }

    public static void setData(String key, String value) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.set(key, value);
        } catch (Exception e) {
            logger.error("Failed to set data", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public static String getData(String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("Failed to set data", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }

        return null;
    }

    public static void expire(String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.expire(key, 0);
        } catch (Exception e) {
            logger.error("Failed to set data", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}
