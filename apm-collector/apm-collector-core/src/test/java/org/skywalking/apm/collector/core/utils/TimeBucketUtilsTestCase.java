package org.skywalking.apm.collector.core.utils;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;

/**
 * @author pengys5
 */
public class TimeBucketUtilsTestCase {

    @Test
    public void testGetFiveSecondTimeBucket() {
        long[] timeBuckets = TimeBucketUtils.INSTANCE.getFiveSecondTimeBuckets(20170804224810L);
        Assert.assertEquals(20170804224810L, timeBuckets[0]);
        Assert.assertEquals(20170804224809L, timeBuckets[1]);
        Assert.assertEquals(20170804224808L, timeBuckets[2]);
        Assert.assertEquals(20170804224807L, timeBuckets[3]);
        Assert.assertEquals(20170804224806L, timeBuckets[4]);
    }

    @Test
    public void testChangeTimeBucket2TimeStamp() {
        long timeStamp = TimeBucketUtils.INSTANCE.changeTimeBucket2TimeStamp(TimeBucketUtils.TimeBucketType.MINUTE.name(), 201708120810L);
        long minute = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(timeStamp);
        Assert.assertEquals(201708120810L, minute);
    }
}
