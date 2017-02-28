package com.a.eye.skywalking.api.logging;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/28.
 */
public class ThrowableFormatterTest {
    @Test
    public void testFormat(){
        NullPointerException exception = new NullPointerException();
        String formatLines = ThrowableFormatter.format(exception);
        String[] lines = formatLines.split("\n");
        Assert.assertEquals("java.lang.NullPointerException", lines[0]);
        Assert.assertEquals("\tat com.a.eye.skywalking.api.logging.ThrowableFormatterTest.testFormat(ThrowableFormatterTest.java:12)", lines[1]);
    }
}
