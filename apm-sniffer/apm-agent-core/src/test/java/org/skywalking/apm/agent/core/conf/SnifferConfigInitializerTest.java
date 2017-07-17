package org.skywalking.apm.agent.core.conf;

import org.junit.AfterClass;
import org.junit.Test;
import org.skywalking.apm.agent.core.logging.LogLevel;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SnifferConfigInitializerTest {

    @Test
    public void testLoadConfigFromJavaAgentDir() {
        System.setProperty("applicationCode", "testApp");
        System.setProperty("servers", "127.0.0.1:8090");
        SnifferConfigInitializer.initialize();
        assertThat(Config.Agent.APPLICATION_CODE, is("testApp"));
        assertThat(Config.Collector.SERVERS, is("127.0.0.1:8090"));
        assertThat(Config.Logging.LEVEL, is(LogLevel.INFO));
    }

    @AfterClass
    public static void clear() {
        Config.Logging.LEVEL = LogLevel.DEBUG;
    }
}
