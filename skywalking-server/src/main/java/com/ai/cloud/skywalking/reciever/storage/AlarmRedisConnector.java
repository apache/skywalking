package com.ai.cloud.skywalking.reciever.storage;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;

/**
 * 告警的redis连接器，用于管理redis连接
 * 
 * @author wusheng
 *
 */
public class AlarmRedisConnector {
	private static JedisPool jedisPool;

	static {
		new RedisInspector().connect().start();
	}

	public static Jedis getJedis() {
		if (Config.Alarm.ALARM_OFF_FLAG) {
			return null;
		} else {
			return jedisPool.getResource();
		}
	}

	public static void reportJedisFailure() {
		RedisInspector.needConnectInit = true;
	}

	private static class RedisInspector extends Thread {
		private static Logger logger = LogManager
				.getLogger(RedisInspector.class);

		private static boolean needConnectInit = true;

		private String[] config;

		public RedisInspector() {
			super("RedisInspectorThread");
			String redisServerConfig = Config.Redis.REDIS_SERVER;
			if (redisServerConfig == null || redisServerConfig.length() <= 0) {
				logger.error("Redis server is not setting. Switch off alarm module. ");
				Config.Alarm.ALARM_OFF_FLAG = true;
			} else {
				config = redisServerConfig.split(":");
				if (config.length != 2) {
					logger.error("Redis server address is illegal setting, need to be 'ip:port'. Switch off alarm module. ");
					Config.Alarm.ALARM_OFF_FLAG = true;
				}
			}
		}

		private RedisInspector connect() {
			if (jedisPool != null && !jedisPool.isClosed()) {
				jedisPool.close();
			}

			GenericObjectPoolConfig genericObjectPoolConfig = buildGenericObjectPoolConfig();
			jedisPool = new JedisPool(genericObjectPoolConfig, config[0],
					Integer.valueOf(config[1]));
			// Test connect redis.
			Jedis jedis = null;
			try {
				jedis = jedisPool.getResource();
				jedis.get("ok");
				needConnectInit = false;
			} catch (Exception e) {
				logger.error("can't connect to redis["
						+ Config.Redis.REDIS_SERVER + "]", e);
			} finally {
				if (jedis != null) {
					jedis.close();
				}
			}
			return this;
		}

		@Override
		public void run() {
			if (Config.Alarm.ALARM_OFF_FLAG)
				return;

			while (true) {
				try {
					if (needConnectInit) {
						connect();
					}

					if (needConnectInit) {
						ServerHealthCollector.getCurrentHeathReading(null)
								.updateData(ServerHeathReading.ERROR,
										"alarm redis connect failue.");
					} else {
						ServerHealthCollector.getCurrentHeathReading(null)
								.updateData(ServerHeathReading.INFO,
										"alarm redis connectted.");
					}
				} catch (Throwable t) {
					logger.error("redis init connect failue", t);
				}

				try {
					Thread.sleep(Config.Alarm.ALARM_REDIS_INSPECTOR_INTERVAL);
				} catch (InterruptedException e) {
					logger.error("Failure sleep.", e);
				}
			}
		}

		private GenericObjectPoolConfig buildGenericObjectPoolConfig() {
			GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
			genericObjectPoolConfig.setTestOnBorrow(true);
			genericObjectPoolConfig.setMaxIdle(Config.Redis.REDIS_MAX_IDLE);
			genericObjectPoolConfig.setMinIdle(Config.Redis.REDIS_MIN_IDLE);
			genericObjectPoolConfig.setMaxTotal(Config.Redis.REDIS_MAX_TOTAL);
			return genericObjectPoolConfig;
		}
	}
}
