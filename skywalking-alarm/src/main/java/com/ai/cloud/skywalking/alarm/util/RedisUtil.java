package com.ai.cloud.skywalking.alarm.util;

import com.ai.cloud.skywalking.alarm.conf.Config;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisUtil {

    private static Logger logger = LogManager.getLogger(RedisUtil.class);
    private static JedisPool jedisPool;
    private static String[] config;

    static {
        GenericObjectPoolConfig genericObjectPoolConfig = buildGenericObjectPoolConfig();
        String redisServerConfig = Config.Alarm.REDIS_SERVER;
        if (redisServerConfig == null || redisServerConfig.length() <= 0) {
            logger.error("Redis server is not setting. Switch off alarm module. ");
        } else {
            config = redisServerConfig.split(":");
            if (config.length != 2) {
                logger.error("Redis server address is illegal setting, need to be 'ip:port'. Switch off alarm module. ");
                Config.Alarm.ALARM_OFF_FLAG = true;
            } else {
                jedisPool = new JedisPool(genericObjectPoolConfig, config[0],
                        Integer.valueOf(config[1]));
            }
        }
    }

    private static GenericObjectPoolConfig buildGenericObjectPoolConfig() {
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setTestOnBorrow(true);
        genericObjectPoolConfig.setMaxIdle(Config.Alarm.REDIS_MAX_IDLE);
        genericObjectPoolConfig.setMinIdle(Config.Alarm.REDIS_MIN_IDLE);
        genericObjectPoolConfig.setMaxTotal(Config.Alarm.REDIS_MAX_TOTAL);
        return genericObjectPoolConfig;
    }

    public static Jedis getRedisClient(){
        return jedisPool.getResource();
    }

}
