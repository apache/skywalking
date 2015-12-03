package com.ai.cloud.skywalking.reciever.storage.chain.alarm;

import com.ai.cloud.skywalking.reciever.conf.Config;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisAccessController {

    private static Logger logger = LogManager.getLogger(RedisAccessController.class);
    private static JedisPool jedisPool;
    private static Object lock = new Object();

    static {
        GenericObjectPoolConfig genericObjectPoolConfig = buildGenericObjectPoolConfig();
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
            jedis.connect();
            return executor.exec(jedis);
        } catch (Exception e) {
            jedisPool = null;
            logger.error("Failed to connect redis", e);
            // 启动备用Redis
            if (jedisPool == null) {
                synchronized (lock) {
                    if (jedisPool == null) {
                        // 生成备份Redis的Redis Client Pool
                        GenericObjectPoolConfig genericObjectPoolConfig = buildGenericObjectPoolConfig();
                        String bakRedisServerConfig = Config.Alarm.BAK_REDIS_SERVER_CONFIG;
                        if (bakRedisServerConfig == null || bakRedisServerConfig.length() <= 0) {
                            logger.error("Bak Redis server config is null.");
                        }

                        String[] config = bakRedisServerConfig.split(":");
                        if (config.length != 2) {
                            logger.error("Bak Redis server config is illegal");
                        }

                        jedisPool =
                                new JedisPool(genericObjectPoolConfig, config[0], Integer.valueOf(config[1]));
                        try {
                            jedis = jedisPool.getResource();
                            jedis.connect();
                            jedis.get("ok");
                        } catch (Exception ex) {
                            logger.error("Failed to connect bak redis server.", ex);
                            // 备份Redis的都失败了，没有想好怎么提示
                            //System.exit(-1);
                        } finally {
                            if (jedis != null) {
                                jedis.close();
                            }
                        }
                    }
                }
                // 重新再获取Redis Client返回执行
                jedis = jedisPool.getResource();
                jedis.connect();
                return executor.exec(jedis);
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
