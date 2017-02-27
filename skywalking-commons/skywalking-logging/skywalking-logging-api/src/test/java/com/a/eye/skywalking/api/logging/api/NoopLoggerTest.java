package com.a.eye.skywalking.api.logging.api;

import org.junit.Assert;
import org.junit.Test;

import static com.a.eye.skywalking.api.logging.api.NoopLogger.INSTANCE;

/**
 * Created by wusheng on 2017/2/27.
 */
public class NoopLoggerTest {
    @Test
    public void testOnNothing(){
        Assert.assertFalse(INSTANCE.isDebugEnable());
        Assert.assertFalse(INSTANCE.isInfoEnable());
        Assert.assertFalse(INSTANCE.isErrorEnable());
        Assert.assertFalse(INSTANCE.isWarnEnable());

        INSTANCE.debug("Any string");
        INSTANCE.debug("Any string", new Object[0]);
        INSTANCE.info("Any string");
        INSTANCE.info("Any string", new Object[0]);
        INSTANCE.warn("Any string", new Object[0]);
        INSTANCE.warn("Any string", new Object[0], new NullPointerException());
        INSTANCE.error("Any string");
        INSTANCE.error("Any string", new NullPointerException());
        INSTANCE.error("Any string", new Object[0], new NullPointerException());
    }
}
