package org.skywalking.apm.agent.core.logging;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.skywalking.apm.agent.core.conf.Constants;

import java.io.PrintStream;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;

/**
 * Created by wusheng on 2017/2/28.
 */
public class EasyLoggerTest {
    private static PrintStream outRef;
    private static PrintStream errRef;

    @BeforeClass
    public static void initAndHoldOut() {
        outRef = System.out;
        errRef = System.err;
    }

    @Test
    public void testLog() {
        PrintStream output = Mockito.mock(PrintStream.class);
        System.setOut(output);
        PrintStream err = Mockito.mock(PrintStream.class);
        System.setErr(err);
        EasyLogger logger = new EasyLogger(EasyLoggerTest.class);

        Assert.assertTrue(logger.isDebugEnable());
        Assert.assertTrue(logger.isInfoEnable());
        Assert.assertTrue(logger.isWarnEnable());
        Assert.assertTrue(logger.isErrorEnable());

        logger.debug("hello world");
        logger.debug("hello {}", "world");
        logger.info("hello world");
        logger.info("hello {}", "world");

        logger.warn("hello {}", "world");
        logger.warn("hello world");
        logger.error("hello world");
        logger.error("hello world", new NullPointerException());
        logger.error(new NullPointerException(), "hello {}", "world");

        Mockito.verify(output, times(9))
            .println(anyString());
    }

    @Test
    public void testFormat() {
        NullPointerException exception = new NullPointerException();
        EasyLogger logger = new EasyLogger(EasyLoggerTest.class);
        String formatLines = logger.format(exception);
        String[] lines = formatLines.split(Constants.LINE_SEPARATOR);
        Assert.assertEquals("java.lang.NullPointerException", lines[1]);
        Assert.assertEquals("\tat org.skywalking.apm.agent.core.logging.EasyLoggerTest.testFormat(EasyLoggerTest.java:58)", lines[2]);
    }

    @AfterClass
    public static void reset() {
        System.setOut(outRef);
        System.setErr(errRef);
    }
}
