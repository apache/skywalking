package com.ai.cloud.skywalking.reciever.storage.chain.alarm;

import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXPIRE_SECONDS;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import redis.clients.jedis.Jedis;

import com.ai.cloud.skywalking.reciever.util.RedisConnector;

public abstract class AbstractSpanChecker implements ISpanChecker {
	private static Logger logger = LogManager.getLogger(AbstractSpanChecker.class);
	
	protected void saveAlarmMessage(String key, String traceId, String alarmMsg) {
        Jedis jedis = null;
        try {
            jedis = RedisConnector.getJedis();
            jedis.hsetnx(key, traceId, alarmMsg);
            jedis.expire(key, ALARM_EXPIRE_SECONDS);
        } catch (Exception e) {
        	RedisConnector.reportJedisFailure();
            logger.error("Failed to set data.", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}
