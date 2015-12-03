package com.ai.cloud.skywalking.reciever.storage.chain.alarm;

import com.ai.cloud.skywalking.reciever.conf.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;

import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXPIRE_SECONDS;

public class AlarmMessageStorage {

    private static Logger logger = LogManager.getLogger(AlarmMessageStorage.class);

    private static boolean exist(final String key, final String field) {
        return RedisAccessController.redis(new RedisAccessController.Executor<Boolean>() {
            @Override
            public Boolean exec(Jedis jedis) {
                if (jedis != null) {
                    return jedis.hexists(key, field);
                }
                return false;
            }
        });
    }

    private static boolean exist(final String key) {
        return RedisAccessController.redis(new RedisAccessController.Executor<Boolean>() {
            @Override
            public Boolean exec(Jedis jedis) {
                if (jedis != null) {
                    return jedis.exists(key);
                }
                return false;
            }
        });
    }

    private static Long set(final String key, final String field, final String value) {
        return RedisAccessController.redis(new RedisAccessController.Executor<Long>() {
            @Override
            public Long exec(Jedis jedis) {
                if (jedis != null) {
                    if (exist(key, field)) {
                        return null;
                    }
                    jedis.hset(key, field, value);
                    return jedis.expire(key, ALARM_EXPIRE_SECONDS);
                }
                return null;
            }
        });

    }

    public static void saveAlarmMessage(String key, String traceId) {
        if (Config.Alarm.ALARM_OFF_FLAG)
            return;
        set(key, traceId, "");
    }

}
