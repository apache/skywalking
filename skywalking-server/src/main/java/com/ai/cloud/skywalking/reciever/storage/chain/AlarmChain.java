package com.ai.cloud.skywalking.reciever.storage.chain;

import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXCEPTION_STACK_LENGTH;
import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXPIRE_SECONDS;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import redis.clients.jedis.Jedis;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.storage.AlarmRedisConnector;
import com.ai.cloud.skywalking.reciever.storage.Chain;
import com.ai.cloud.skywalking.reciever.storage.IStorageChain;

public class AlarmChain implements IStorageChain {
    private static Logger logger = LogManager.getLogger(AlarmChain.class);

    @Override
    public void doChain(List<Span> spans, Chain chain) {
        for (Span span : spans) {
            if (span.getStatusCode() != 1)
                continue;
            String exceptionStack = span.getExceptionStack();
            if (exceptionStack == null) {
                exceptionStack = "";
            } else if (exceptionStack.length() > ALARM_EXCEPTION_STACK_LENGTH) {
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
            jedis = AlarmRedisConnector.getJedis();
            if(jedis == null){
            	logger.error("Failed to set data. can't get jedis.");
            	return;
            }
            jedis.hsetnx(key, traceId, exceptionMsgOutline);
            jedis.expire(key, ALARM_EXPIRE_SECONDS);
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
