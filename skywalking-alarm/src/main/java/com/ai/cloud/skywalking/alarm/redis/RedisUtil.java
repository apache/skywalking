package com.ai.cloud.skywalking.alarm.redis;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.ArrayList;
import java.util.List;

public class RedisUtil {
    private static Logger logger = LogManager.getLogger(RedisUtil.class);
    private static JedisPool jedisPool;
    private static String[] config;
    private static RedisInspector connector = new RedisInspector();
    private static Object lock = new Object();

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
                // Test connect redis.
                Jedis jedis = null;
                try {
                    jedis = jedisPool.getResource();
                } catch (Exception e) {
                    handleFailedToConnectRedisServerException(e);
                    logger.error("can't connect to redis["
                            + Config.Alarm.REDIS_SERVER + "]", e);
                } finally {
                    if (jedis != null) {
                        jedis.close();
                    }
                }
            }
        }
    }

    public static List<String> getAlarmMessage(ApplicationInfo applicationInfo) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return new ArrayList<String>(jedis.hgetAll(generateAlarmKey(applicationInfo)).values());
        } catch (Exception e) {
            handleFailedToConnectRedisServerException(e);
            logger.error("Failed to set data.", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }

        return new ArrayList<String>();
    }

    private static String generateAlarmKey(ApplicationInfo applicationInfo) {
        return applicationInfo.getUId() + "-" + applicationInfo.getAppId() + "-"
                + ((System.currentTimeMillis() / (10000 * 6))
                - applicationInfo.getConfigArgsDescriber().getPeriod());
    }

    private static GenericObjectPoolConfig buildGenericObjectPoolConfig() {
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setTestOnBorrow(true);
        genericObjectPoolConfig.setMaxIdle(Config.Alarm.REDIS_MAX_IDLE);
        genericObjectPoolConfig.setMinIdle(Config.Alarm.REDIS_MIN_IDLE);
        genericObjectPoolConfig.setMaxTotal(Config.Alarm.REDIS_MAX_TOTAL);
        return genericObjectPoolConfig;
    }

    private static void handleFailedToConnectRedisServerException(Exception e) {
        if (e instanceof JedisConnectionException) {
            // 发生连接不上Redis
            if (connector == null || !connector.isAlive()) {
                synchronized (lock) {
                    if (!connector.isAlive()) {
                        // 启动巡检线程
                        connector.start();
                    }
                }
            }
        }
    }

    private static class RedisInspector extends Thread {
        @Override
        public void run() {
            logger.debug("Connecting to redis....");
            Jedis jedis;
            while (true) {
                try {
                    jedisPool = new JedisPool(buildGenericObjectPoolConfig(),
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
            logger.debug("Connected to redis success. Open alarm function.");
            Config.Alarm.ALARM_OFF_FLAG = false;
            // 清理当前线程
            connector = null;
        }
    }

}
