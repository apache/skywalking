package org.skywalking.apm.logging;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/27.
 */
public class NoopLoggerTest {
    @Test
    public void testOnNothing() {
        Assert.assertFalse(NoopLogger.INSTANCE.isDebugEnable());
        Assert.assertFalse(NoopLogger.INSTANCE.isInfoEnable());
        Assert.assertFalse(NoopLogger.INSTANCE.isErrorEnable());
        Assert.assertFalse(NoopLogger.INSTANCE.isWarnEnable());

        NoopLogger.INSTANCE.debug("Any string");
        NoopLogger.INSTANCE.debug("Any string", new Object[0]);
        NoopLogger.INSTANCE.info("Any string");
        NoopLogger.INSTANCE.info("Any string", new Object[0]);
        NoopLogger.INSTANCE.warn("Any string", new Object[0]);
        NoopLogger.INSTANCE.warn("Any string", new Object[0], new NullPointerException());
        NoopLogger.INSTANCE.error("Any string");
        NoopLogger.INSTANCE.error("Any string", new NullPointerException());
    }
}
