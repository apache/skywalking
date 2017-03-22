package com.a.eye.skywalking.api.logging;

import com.a.eye.skywalking.api.conf.Config;
import java.io.PrintStream;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;

/**
 * Created by wusheng on 2017/2/28.
 */
public class EasyLoggerTest {
    private static PrintStream outRef;
    private static PrintStream errRef;

    @BeforeClass
    public static void initAndHoldOut(){
        outRef = System.out;
        errRef = System.err;
    }

    @Test
    public void testLog(){
        Config.Agent.IS_PREMAIN_MODE = false;

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
        logger.error(new NullPointerException(),"hello {}", "world");

        Mockito.verify(output,times(4))
            .println(anyString());
        Mockito.verify(err,times(7))
            .println(anyString());
    }

    @AfterClass
    public static void reset(){
        System.setOut(outRef);
        System.setErr(errRef);
    }
}
