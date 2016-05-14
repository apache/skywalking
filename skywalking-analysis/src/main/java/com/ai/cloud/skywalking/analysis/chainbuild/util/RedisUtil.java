package com.ai.cloud.skywalking.analysis.chainbuild.util;

import com.ai.cloud.skywalking.analysis.config.Config;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Created by xin on 16-5-13.
 */
public class RedisUtil {

    private static Logger logger = LogManager.getLogger(RedisUtil.class);

    private static JedisPool jedisPool;

    private static boolean turn_on = true;

    static {
        try {
            GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
            jedisPool = new JedisPool(genericObjectPoolConfig, Config.Redis.HOST, Config.Redis.PORT);
        } catch (Exception e) {
            logger.error("Failed to create jedis pool", e);
            turn_on = false;
        }
    }


    public static void autoIncrement(String key) {
        if (!turn_on) {
            return;
        }

        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.incrBy(key, 1);
        } catch (Exception e) {
            logger.error("Failed to auto increment .", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }


    public static void clearData(String key) {
        if (!turn_on) {
            return;
        }

        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.setnx(key, "0");
        } catch (Exception e) {
            logger.error("Failed to auto increment .", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

}
