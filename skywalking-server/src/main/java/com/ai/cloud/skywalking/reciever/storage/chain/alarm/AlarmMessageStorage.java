package com.ai.cloud.skywalking.reciever.storage.chain.alarm;

import redis.clients.jedis.Jedis;

import java.util.Collection;

import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXPIRE_SECONDS;

public class AlarmMessageStorage {

    private static boolean exist(final String key, final String field) {
        return RedisAccessController.redis(new RedisAccessController.Executor<Boolean>() {
            @Override
            public Boolean exec(Jedis jedis) {
                return jedis.hexists(key, field);
            }
        });
    }

    private static boolean exist(final String key) {
        return RedisAccessController.redis(new RedisAccessController.Executor<Boolean>() {
            @Override
            public Boolean exec(Jedis jedis) {
                return jedis.exists(key);
            }
        });
    }

    private static Long set(final String key, final String field, final String value) {
        return RedisAccessController.redis(new RedisAccessController.Executor<Long>() {
            @Override
            public Long exec(Jedis jedis) {
                if (exist(key, field)) {
                    return null;
                }
                jedis.hset(key, field, value);
                return jedis.expire(key, ALARM_EXPIRE_SECONDS);
            }
        });

    }

    private static Collection<String> get(final String key) {
        return RedisAccessController.redis(new RedisAccessController.Executor<Collection<String>>() {
            @Override
            public Collection<String> exec(Jedis jedis) {
                if (!exist(key)) {
                    return null;
                }
                return jedis.hgetAll(key).values();
            }
        });
    }


    public static void saveAlarmMessage(String key, String traceId) {
        set(key, traceId, "");
    }

    public static Collection<String> getAlarmMessage(String key) {
        return get(key);
    }

}
