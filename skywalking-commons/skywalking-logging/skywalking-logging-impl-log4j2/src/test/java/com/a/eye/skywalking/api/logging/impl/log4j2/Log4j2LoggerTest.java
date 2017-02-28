package com.a.eye.skywalking.api.logging.impl.log4j2;

import org.apache.logging.log4j.Logger;
import org.junit.Test;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by wusheng on 2017/2/28.
 */
public class Log4j2LoggerTest {
    @Test
    public void testLogProxy(){
        Logger mockLogger = spy(Logger.class);
        Log4j2Logger logger = new Log4j2Logger(mockLogger);

        logger.isDebugEnable();
        verify(mockLogger, times(1)).isDebugEnabled();
        logger.isInfoEnable();
        verify(mockLogger, times(1)).isInfoEnabled();
        logger.isWarnEnable();
        verify(mockLogger, times(1)).isWarnEnabled();
        logger.isErrorEnable();
        verify(mockLogger, times(1)).isErrorEnabled();

        logger.debug("string");
        verify(mockLogger, times(1)).debug("string");
        logger.debug("string", "arg1", "args");
        verify(mockLogger, times(1)).debug("string", new Object[]{"arg1", "args"});

        logger.info("string");
        verify(mockLogger, times(1)).info("string");
        logger.info("string", "arg1", "args");
        verify(mockLogger, times(1)).info("string", new Object[]{"arg1", "args"});

        logger.warn("string", "arg1", "args");
        verify(mockLogger, times(1)).warn("string", new Object[]{"arg1", "args"});

        logger.error("string");
        verify(mockLogger, times(1)).error("string");
        NullPointerException exception = new NullPointerException();
        logger.error("string", exception);
        verify(mockLogger, times(1)).error("string", exception);
    }
}
