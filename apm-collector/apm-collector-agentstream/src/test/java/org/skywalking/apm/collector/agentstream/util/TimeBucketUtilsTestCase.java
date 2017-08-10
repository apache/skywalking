package org.skywalking.apm.collector.agentstream.util;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.stream.worker.util.TimeBucketUtils;

/**
 * @author pengys5
 */
public class TimeBucketUtilsTestCase {

    @Test
    public void testUTCLocation() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        long timeBucket = 201703310915L;
        long changedTimeBucket = TimeBucketUtils.INSTANCE.changeToUTCTimeBucket(timeBucket);
        Assert.assertEquals(201703310115L, changedTimeBucket);
    }

    @Test
    public void testUTC8Location() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"));
        long timeBucket = 201703310915L;
        long changedTimeBucket = TimeBucketUtils.INSTANCE.changeToUTCTimeBucket(timeBucket);
        Assert.assertEquals(201703310915L, changedTimeBucket);
    }

    @Test
    public void testGetSecondTimeBucket() {
        long timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(1490922929258L);
        Assert.assertEquals(20170331091529L, timeBucket);
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
