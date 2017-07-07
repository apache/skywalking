package org.skywalking.apm.agent.core.conf;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.agent.core.logging.LogLevel;

/**
 * @author wusheng
 */
public class SnifferConfigInitializerTest {

    @Test
    public void testInitialize() {
        SnifferConfigInitializer.initialize();

        Assert.assertEquals("crmApp", Config.Agent.APPLICATION_CODE);
        Assert.assertEquals("127.0.0.1:8080", Config.Collector.SERVERS);

        Assert.assertNotNull(Config.Logging.DIR);
        Assert.assertNotNull(Config.Logging.FILE_NAME);
        Assert.assertNotNull(Config.Logging.MAX_FILE_SIZE);
        Assert.assertNotNull(Config.Logging.FILE_NAME);
        Assert.assertEquals(LogLevel.INFO, Config.Logging.LEVEL);
    }

    @AfterClass
    public static void clear() {
        Config.Logging.LEVEL = LogLevel.DEBUG;
    }
}
