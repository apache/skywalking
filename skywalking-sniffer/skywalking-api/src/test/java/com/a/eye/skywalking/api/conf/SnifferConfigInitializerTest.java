package com.a.eye.skywalking.api.conf;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import static com.a.eye.skywalking.api.logging.LogLevel.*;

/**
 * @author wusheng
 */
public class SnifferConfigInitializerTest {

    @Test
    public void testInitialize(){
        SnifferConfigInitializer.initialize();

        Assert.assertEquals("crmApp", Config.Agent.APPLICATION_CODE);
        Assert.assertEquals("127.0.0.1:8080", Config.Collector.SERVERS);

        Assert.assertNotNull(Config.Buffer.SIZE);
        Assert.assertNotNull(Config.Logging.DIR);
        Assert.assertNotNull(Config.Logging.FILE_NAME);
        Assert.assertNotNull(Config.Logging.MAX_FILE_SIZE);
        Assert.assertNotNull(Config.Logging.FILE_NAME);
        Assert.assertEquals(INFO, Config.Logging.LEVEL);
    }

    @AfterClass
    public static void clear(){
        Config.Logging.LEVEL = DEBUG;
    }
}
