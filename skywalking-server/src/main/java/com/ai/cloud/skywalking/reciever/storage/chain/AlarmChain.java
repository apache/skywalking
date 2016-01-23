package com.ai.cloud.skywalking.reciever.storage.chain;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.storage.Chain;
import com.ai.cloud.skywalking.reciever.storage.IStorageChain;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.List;

import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXPIRE_SECONDS;
import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXCEPTION_STACK_LENGTH;

public class AlarmChain implements IStorageChain {
	private static Logger logger = LogManager.getLogger(AlarmChain.class);
	private static JedisPool jedisPool;
	private static String[] config;
	private static Object lock = new Object();
	private static RedisInspector connector = new RedisInspector();;

	static {
		GenericObjectPoolConfig genericObjectPoolConfig = buildGenericObjectPoolConfig();
		String redisServerConfig = Config.Alarm.REDIS_SERVER;
		if (redisServerConfig == null || redisServerConfig.length() <= 0) {
			logger.error("Redis server is not setting. Switch off alarm module. ");
			Config.Alarm.ALARM_OFF_FLAG = true;
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

	@Override
	public void doChain(List<Span> spans, Chain chain) {
		for (Span span : spans) {
			if (span.getStatusCode() != 1)
				continue;
			String exceptionStack = span.getExceptionStack();
			if(exceptionStack == null){
				exceptionStack = "";
			}else if(exceptionStack.length() > ALARM_EXCEPTION_STACK_LENGTH){
				exceptionStack = exceptionStack.substring(0, ALARM_EXCEPTION_STACK_LENGTH);
			}
			saveAlarmMessage(generateAlarmKey(span), span.getTraceId(), exceptionStack);
		}
		chain.doChain(spans);
	}

	private String generateAlarmKey(Span span) {
		return span.getUserId() + "-" + span.getApplicationId() + "-"
				+ (System.currentTimeMillis() / (10000 * 6));
	}

	private void saveAlarmMessage(String key, String traceId, String exceptionMsgOutline) {
		if (Config.Alarm.ALARM_OFF_FLAG) {
			return;
		}

		Jedis jedis = null;
		try {
			jedis = jedisPool.getResource();
			jedis.hsetnx(key, traceId, exceptionMsgOutline);
			jedis.expire(key, ALARM_EXPIRE_SECONDS);
		} catch (Exception e) {
			handleFailedToConnectRedisServerException(e);
			logger.error("Failed to set data.", e);
		} finally {
			if (jedis != null) {
				jedis.close();
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

	private static GenericObjectPoolConfig buildGenericObjectPoolConfig() {
		GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
		genericObjectPoolConfig.setTestOnBorrow(true);
		genericObjectPoolConfig.setMaxIdle(Config.Alarm.REDIS_MAX_IDLE);
		genericObjectPoolConfig.setMinIdle(Config.Alarm.REDIS_MIN_IDLE);
		genericObjectPoolConfig.setMaxTotal(Config.Alarm.REDIS_MAX_TOTAL);
		return genericObjectPoolConfig;
	}
}
