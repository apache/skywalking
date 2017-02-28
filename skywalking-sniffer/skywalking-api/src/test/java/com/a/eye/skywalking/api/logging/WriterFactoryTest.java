package com.a.eye.skywalking.api.logging;

import com.a.eye.skywalking.api.conf.Config;
import java.io.PrintStream;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Created by wusheng on 2017/2/28.
 */
public class WriterFactoryTest {
    private static PrintStream errRef;

    @BeforeClass
    public static void initAndHoldOut(){
        errRef = System.err;
    }

    /**
     * During this test case,
     * reset {@link System#out} to a Mock object, for avoid a console system.error.
     */
    @Test
    public void testGetLogWriter(){
        Config.SkyWalking.IS_PREMAIN_MODE = true;
        PrintStream mockStream = Mockito.mock(PrintStream.class);
        System.setErr(mockStream);
        Assert.assertEquals(SyncFileWriter.instance(), WriterFactory.getLogWriter());

        Config.SkyWalking.IS_PREMAIN_MODE = false;
        Assert.assertTrue(WriterFactory.getLogWriter() instanceof STDOutWriter);
    }

    @AfterClass
    public static void reset(){
        Config.SkyWalking.IS_PREMAIN_MODE = false;
        System.setErr(errRef);
    }
}
