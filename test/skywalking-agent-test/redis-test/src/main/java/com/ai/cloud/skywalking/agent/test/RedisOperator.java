package com.ai.cloud.skywalking.agent.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by xin on 16-6-5.
 */
public class RedisOperator {

    private static Logger logger = LogManager.getLogger(RedisOperator.class);
    private static JedisPool jedisPool = null;

    static {
        InputStream inputStream = RedisOperator.class.getResourceAsStream("/redis.conf");
        Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            logger.error("Failed to load redis.conf", e);
            System.exit(-1);
        }
        jedisPool = new JedisPool(properties.getProperty("redis.ip", "127.0.0.1"),
                Integer.parseInt(properties.getProperty("redis.port", "6379")));
    }


    public static void setData(String key, String value) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.set(key, value);
        } catch (Exception e) {
            logger.error(e);
            throw e;
        } finally {
            if (jedis != null)
                jedis.close();
        }
    }

    public static String getData(String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return jedis.get(key);
        } catch (Exception e) {
            logger.error(e);
            throw e;
        } finally {
            if (jedis != null)
                jedis.close();
        }

    }

    public static void delData(String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.del(key);
        } catch (Exception e) {
            logger.error(e);
            throw e;
        } finally {
            if (jedis != null)
                jedis.close();
        }
    }
}
