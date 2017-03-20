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
        Config.SkyWalking.IS_PREMAIN_MODE = false;
        SnifferConfigInitializer.initialize();

        Assert.assertEquals("crmApp", Config.SkyWalking.APPLICATION_CODE);
        Assert.assertEquals("127.0.0.1:8080", Config.SkyWalking.SERVERS);

        Assert.assertNotNull(Config.Disruptor.BUFFER_SIZE);
        Assert.assertNotNull(Config.Logging.LOG_DIR_NAME);
        Assert.assertNotNull(Config.Logging.LOG_FILE_NAME);
        Assert.assertNotNull(Config.Logging.MAX_LOG_FILE_LENGTH);
        Assert.assertNotNull(Config.Logging.SYSTEM_ERROR_LOG_FILE_NAME);
    }

    @Test(expected = ExceptionInInitializerError.class)
    public void testErrorInitialize(){
        Config.SkyWalking.IS_PREMAIN_MODE = true;
        SnifferConfigInitializer.initialize();
    }

    @AfterClass
    public static void reset(){
        Config.SkyWalking.IS_PREMAIN_MODE = false;
    }
}
