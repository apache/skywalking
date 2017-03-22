package com.a.eye.skywalking.api.conf;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class SnifferConfigInitializerTest {

    @Test
    public void testInitialize(){
        Config.Agent.IS_PREMAIN_MODE = false;
        SnifferConfigInitializer.initialize();

        Assert.assertEquals("crmApp", Config.Agent.APPLICATION_CODE);
        Assert.assertEquals("127.0.0.1:8080", Config.Collector.SERVERS);

        Assert.assertNotNull(Config.Buffer.SIZE);
        Assert.assertNotNull(Config.Logging.LOG_DIR_NAME);
        Assert.assertNotNull(Config.Logging.LOG_FILE_NAME);
        Assert.assertNotNull(Config.Logging.MAX_LOG_FILE_LENGTH);
        Assert.assertNotNull(Config.Logging.SYSTEM_ERROR_LOG_FILE_NAME);
    }

    @Test(expected = ExceptionInInitializerError.class)
    public void testErrorInitialize(){
        Config.Agent.IS_PREMAIN_MODE = true;
        SnifferConfigInitializer.initialize();
    }

    @AfterClass
    public static void reset(){
        Config.Agent.IS_PREMAIN_MODE = false;
    }
}
