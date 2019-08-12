package org.apache.skywalking.apm.agent.core.logging.core;

import com.google.common.collect.Lists;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.Constants;
import org.hamcrest.core.StringContains;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.PrintStream;
import java.util.List;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;

/**
 * @author alvin
 */
public class PatternLoggerTest {

    public static final String PATTERN = "%{timestamp}+0800 %{level} [%{agent.service_name},,,] [%{thread}] %{class}:-1 %{msg} %{throwable}";

    private static PrintStream OUT_REF;
    private static PrintStream ERR_REF;

    @BeforeClass
    public static void initAndHoldOut() {
        OUT_REF = System.out;
        ERR_REF = System.err;
    }


    @Test
    public void testLog() {
        PrintStream output = Mockito.mock(PrintStream.class);
        System.setOut(output);
        PrintStream err = Mockito.mock(PrintStream.class);
        System.setErr(err);
        PatternLogger logger = new PatternLogger(PatternLoggerTest.class, PATTERN) {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                String r = format(level, message, e);
                SystemOutWriter.INSTANCE.write(r);
            }
        };

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
    public void testLogWithPlaceHolderKeyWord() {
        final List<String> strings = Lists.newArrayList();
        PatternLogger logger = new PatternLogger(PatternLoggerTest.class, PATTERN) {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                String r = format(level, message, e);
                strings.add(r);
            }
        };
        logger.info("logmsg: $$$$$!@#$%^&*() %{this is message}");
        Assert.assertThat(strings.get(0), StringContains.containsString("INFO [testAppFromSystemEnv,,,] [main] PatternLoggerTest:-1 logmsg: $$$$$!@#$%^&*() %{this is message}"));
    }

    @Test
    public void testLogFormat() {
        String old = Config.Agent.SERVICE_NAME;
        Config.Agent.SERVICE_NAME = "testAppFromSystemEnv";

        final List<String> strings = Lists.newArrayList();
        PatternLogger logger = new PatternLogger(PatternLoggerTest.class, PATTERN) {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                String r = format(level, message, e);
                strings.add(r);
            }
        };
        NullPointerException exception = new NullPointerException();
        logger.error("hello world", exception);
        logger.error("hello world", null);
        String formatLines = strings.get(0);
        String[] lines = formatLines.split(Constants.LINE_SEPARATOR);
        Assert.assertThat(lines[0], StringContains.containsString("ERROR [testAppFromSystemEnv,,,] [main] PatternLoggerTest:-1 hello world "));
        Assert.assertEquals("java.lang.NullPointerException", lines[1]);
        Assert.assertThat(lines[2], StringContains.containsString("PatternLoggerTest.testLogFormat"));
        Assert.assertEquals(strings.get(1).split(Constants.LINE_SEPARATOR).length, 1);

        Config.Agent.SERVICE_NAME = old;
    }


    @AfterClass
    public static void reset() {
        System.setOut(OUT_REF);
        System.setErr(ERR_REF);
    }

}
