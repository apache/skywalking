package org.skywalking.apm.agent.core.logging;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.skywalking.apm.agent.core.conf.Config;

import java.io.PrintStream;

/**
 * Created by wusheng on 2017/2/28.
 */
public class WriterFactoryTest {
    private static PrintStream errRef;

    @BeforeClass
    public static void initAndHoldOut() {
        errRef = System.err;
    }

    /**
     * During this test case,
     * reset {@link System#out} to a Mock object, for avoid a console system.error.
     */
    @Test
    public void testGetLogWriter() {
        PrintStream mockStream = Mockito.mock(PrintStream.class);
        System.setErr(mockStream);
        Assert.assertEquals(SystemOutWriter.INSTANCE, WriterFactory.getLogWriter());

        Config.Logging.DIR = "/only/test/folder";
        Assert.assertTrue(WriterFactory.getLogWriter() instanceof FileWriter);
    }

    @AfterClass
    public static void reset() {
        Config.Logging.DIR = "";
        System.setErr(errRef);
    }
}
