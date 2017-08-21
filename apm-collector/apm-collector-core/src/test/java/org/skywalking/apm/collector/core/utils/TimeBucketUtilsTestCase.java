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
        long fiveSecondTimeBucket = TimeBucketUtils.INSTANCE.getFiveSecondTimeBucket(20170804224812L);
        Assert.assertEquals(20170804224810L, fiveSecondTimeBucket);

        fiveSecondTimeBucket = TimeBucketUtils.INSTANCE.getFiveSecondTimeBucket(20170804224818L);
        Assert.assertEquals(20170804224820L, fiveSecondTimeBucket);

        fiveSecondTimeBucket = TimeBucketUtils.INSTANCE.getFiveSecondTimeBucket(20170804224815L);
        Assert.assertEquals(20170804224815L, fiveSecondTimeBucket);
    }
}
