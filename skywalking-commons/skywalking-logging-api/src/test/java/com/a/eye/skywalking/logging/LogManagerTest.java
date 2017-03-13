package com.a.eye.skywalking.logging;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;

/**
 * @author wusheng
 */
public class LogManagerTest {

    @Test
    public void testGetNoopLogger(){
        ILog logger = LogManager.getLogger(LogManagerTest.class);
        Assert.assertEquals(NoopLogger.INSTANCE, logger);
    }

    @Before
    @After
    public void clear() throws IllegalAccessException {
        MemberModifier.field(LogManager.class, "resolver").set(null, null);
    }


    public class TestLogger implements ILog {

        @Override public void info(String format) {

        }

        @Override public void info(String format, Object... arguments) {

        }

        @Override public void warn(String format, Object... arguments) {

        }

        @Override public void error(String format, Throwable e) {

        }

        @Override public void error(Throwable e, String format, Object... arguments) {

        }

        @Override public boolean isDebugEnable() {
            return false;
        }

        @Override public boolean isInfoEnable() {
            return false;
        }

        @Override public boolean isWarnEnable() {
            return false;
        }

        @Override public boolean isErrorEnable() {
            return false;
        }

        @Override public void debug(String format) {

        }

        @Override public void debug(String format, Object... arguments) {

        }

        @Override public void error(String format) {

        }
    }

}
