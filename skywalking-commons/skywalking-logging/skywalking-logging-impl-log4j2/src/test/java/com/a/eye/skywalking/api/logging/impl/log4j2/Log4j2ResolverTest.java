package com.a.eye.skywalking.api.logging.impl.log4j2;

import com.a.eye.skywalking.api.logging.api.ILog;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/28.
 */
public class Log4j2ResolverTest {
    @Test
    public void testGetLogger() {
        Log4j2Resolver resolver = new Log4j2Resolver();
        ILog logger = resolver.getLogger(Log4j2ResolverTest.class);

        Assert.assertTrue(logger instanceof Log4j2Logger);
    }
}
