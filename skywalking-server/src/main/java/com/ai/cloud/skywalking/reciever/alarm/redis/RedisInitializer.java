package com.ai.cloud.skywalking.reciever.alarm.redis;

import com.ai.cloud.skywalking.reciever.conf.Config;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisInitializer {

    private static Logger logger = LogManager.getLogger(RedisInitializer.class);
    private static JedisPool jedisPool;
    private static final int REDIS_MAX_IDLE = 10;
    private static final int REDIS_MIN_IDLE = 1;
    private static final int REDIS_MAX_TOTAL = 20;

    static {
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setTestOnBorrow(true);
        genericObjectPoolConfig.setMaxIdle(REDIS_MAX_IDLE);
        genericObjectPoolConfig.setMinIdle(REDIS_MIN_IDLE);
        genericObjectPoolConfig.setMaxTotal(REDIS_MAX_TOTAL);

        String redisServerConfig = Config.Alarm.REDIS_SERVER_CONFIG;
        if (redisServerConfig == null || redisServerConfig.length() <= 0) {
            logger.error("Redis server config is null.");
        }

        String[] config = redisServerConfig.split(":");
        if (config.length != 2) {
            logger.error("Redis server config is illegal");
        }

        jedisPool =
                new JedisPool(genericObjectPoolConfig, config[0], Integer.valueOf(config[1]));

        // Test connect redis.
        RedisInitializer.redis(new Executor<String>() {
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
            logger.error("Failed to connect redis", e);
            //TODO 启动备用Redis
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }

        return null;
    }


    public interface Executor<R> {
        R exec(Jedis jedis);
    }
}
