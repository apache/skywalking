package com.a.eye.skywalking.routing.alarm.sender;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.routing.config.Config;
import redis.clients.jedis.Jedis;

/**
 * Created by xin on 2016/12/8.
 */
public class AlarmMessageSender {

    private ILog logger = LogManager.getLogger(AlarmMessageSender.class);

    public void send(String alarmKey, String traceId, String message) {
        Jedis jedis = null;
        try {
            jedis = AlarmRedisConnector.getJedis();
            jedis.hsetnx(alarmKey, traceId, message);
            jedis.expire(alarmKey, Config.Alarm.ALARM_EXPIRE_SECONDS);
        } catch (Exception e) {
            AlarmRedisConnector.reportJedisFailure();
            logger.error("Failed to set data.", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}
