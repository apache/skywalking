package com.ai.cloud.skywalking.reciever.alarm;

import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.conf.ConfigInitializer;
import org.junit.Test;

import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class AlarmOperatorTest {

    @Test
    public void testSaveAlarmMessage() throws Exception {
        Properties properties = new Properties();
        properties.load(AlarmOperatorTest.class.getResourceAsStream("/config.properties"));
        ConfigInitializer.initialize(properties, Config.class);

        String key = UUID.randomUUID().toString();
        AlarmOperator.saveAlarmMessage(key, UUID.randomUUID().toString());
        assertEquals(1, AlarmOperator.getAlarmMessage(key).size());
    }


}