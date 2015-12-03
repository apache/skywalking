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
    private static String[] config;
    private static Object lock = new Object();
    private static RedisConnector connector;

    static {
        GenericObjectPoolConfig genericObjectPoolConfig = buildGenericObjectPoolConfig();
        String redisServerConfig = Config.Alarm.REDIS_SERVER_CONFIG;
        if (redisServerConfig == null || redisServerConfig.length() <= 0) {
            logger.error("Redis server config is null.");
            Config.Alarm.ALARM_OFF_FLAG = true;
        } else {
            config = redisServerConfig.split(":");
            if (config.length != 2) {
                logger.error("Redis server config is illegal");
                Config.Alarm.ALARM_OFF_FLAG = true;
            } else {
                jedisPool =
                        new JedisPool(genericObjectPoolConfig, config[0],
                                Integer.valueOf(config[1]));
                // Test connect redis.
                RedisAccessController.redis(new Executor<String>() {
                    @Override
                    public String exec(Jedis jedis) {
                        // 对Redis Client为空校验
                        if (jedis != null) {
                            return jedis.get("ok");
                        }
                        return null;
                    }
                });
            }
        }

    }

    public static <T> T redis(Executor<T> executor) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return executor.exec(jedis);
        } catch (Exception e) {
            if (e instanceof JedisConnectionException) {
                // 发生连接不上Redis
                if (connector == null || !connector.isAlive()) {
                    synchronized (lock) {
                        if (connector == null || !connector.isAlive()) {
                            // 启动巡检线程
                            connector = new RedisConnector();
                            connector.start();
                        }
                    }
                }
            }
            logger.error("Failed to set data.", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        // 当发生异常的时候，返回的Redis的Client会是null，
        // 需要对Redis Client为空校验
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

    static class RedisConnector extends Thread {
        @Override
        public void run() {
            logger.info("Connecting to redis....");
            Jedis jedis;
            while (true) {
                try {
                    jedisPool =
                            new JedisPool(buildGenericObjectPoolConfig(),
                                    config[0], Integer.valueOf(config[1]));
                    jedis = jedisPool.getResource();
                    jedis.get("ok");
                    break;
                } catch (Exception e) {
                    if (e instanceof JedisConnectionException) {
                        try {
                            Thread.sleep(5000L);
                        } catch (InterruptedException e1) {
                            logger.error("Sleep failed", e);
                        }
                        continue;
                    }
                }
            }
            logger.info("Connected to redis success. Open alarm function.");
            Config.Alarm.ALARM_OFF_FLAG = false;
            // 清理当前线程
            connector = null;
        }
    }


    public interface Executor<R> {
        R exec(Jedis jedis);
    }
}
