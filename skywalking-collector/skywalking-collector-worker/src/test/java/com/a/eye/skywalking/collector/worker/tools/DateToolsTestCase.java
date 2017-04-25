package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.collector.worker.storage.IndexCreator;
import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.powermock.api.mockito.PowerMockito.spy;

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

    @Test
    public void test() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(1490922929258L);
        calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND) - 3);
//        System.out.println(calendar.getTimeInMillis());
        calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND) - 2);
//        System.out.println(calendar.getTimeInMillis());
        calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND) - 2);
//        System.out.println(calendar.getTimeInMillis());
    }
}
