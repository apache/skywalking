package com.a.eye.skywalking.collector.worker.tools;

import org.junit.Assert;
import org.junit.Test;

import java.util.TimeZone;

/**
 * @author pengys5
 */
public class DateToolsTestCase {

    @Test
    public void testUTCLocation() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        long timeSlice = 201703310915L;
        long changedTimeSlice = DateTools.changeToUTCSlice(timeSlice);
        Assert.assertEquals(201703310115L, changedTimeSlice);
    }

    @Test
    public void testUTC8Location() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"));
        long timeSlice = 201703310915L;
        long changedTimeSlice = DateTools.changeToUTCSlice(timeSlice);
        Assert.assertEquals(201703310915L, changedTimeSlice);
    }
}
