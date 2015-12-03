package com.ai.cloud.skywalking.reciever.storage.chain.alarm;

import com.ai.cloud.skywalking.reciever.conf.Config;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisAccessController {

    private static Logger logger = LogManager.getLogger(RedisAccessController.class);
    private static JedisPool jedisPool;

    static {
        GenericObjectPoolConfig genericObjectPoolConfig = buildGenericObjectPoolConfig();
        String redisServerConfig = Config.Alarm.REDIS_SERVER_CONFIG;
        if (redisServerConfig == null || redisServerConfig.length() <= 0) {
            logger.error("Redis server config is null.");
            Config.Alarm.ALARM_OFF_FLAG = true;
        }


        String[] config = redisServerConfig.split(":");
        if (config.length != 2) {
            logger.error("Redis server config is illegal");
            Config.Alarm.ALARM_OFF_FLAG = true;
        }


        jedisPool =
                new JedisPool(genericObjectPoolConfig, config[0], Integer.valueOf(config[1]));

        // Test connect redis.
        RedisAccessController.redis(new Executor<String>() {
            @Override
            public String exec(Jedis jedis) {
                return jedis.get("ok");
            }
        });

    }

    public static <T> T redis(Executor<T> executor) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return executor.exec(jedis);
        } catch (Exception e) {
            logger.error("Failed to set data.", e);
            if (e instanceof JedisConnectionException) {
                logger.error("Failed to connect redis. close alarm function.", e);
                Config.Alarm.ALARM_OFF_FLAG = true;
            }
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }

        return null;
    }

    private static GenericObjectPoolConfig buildGenericObjectPoolConfig() {
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setTestOnBorrow(true);
        genericObjectPoolConfig.setMaxIdle(Config.Alarm.REDIS_MAX_IDLE);
        genericObjectPoolConfig.setMinIdle(Config.Alarm.REDIS_MIN_IDLE);
        genericObjectPoolConfig.setMaxTotal(Config.Alarm.REDIS_MAX_TOTAL);
        return genericObjectPoolConfig;
    }


    public interface Executor<R> {
        R exec(Jedis jedis);
    }
}
