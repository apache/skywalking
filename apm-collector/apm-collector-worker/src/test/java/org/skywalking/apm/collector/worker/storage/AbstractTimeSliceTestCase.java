package org.skywalking.apm.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class AbstractTimeSliceTestCase {

    @Test
    public void test() {
        TimeSlice timeSlice = new TimeSlice(1L, 2L, 3L, 4);
        Assert.assertEquals(1L, timeSlice.getMinute());
        Assert.assertEquals(2L, timeSlice.getHour());
        Assert.assertEquals(3L, timeSlice.getDay());
        Assert.assertEquals(4, timeSlice.getSecond());
    }

    class TimeSlice extends AbstractTimeSlice {
        public TimeSlice(long minute, long hour, long day, int second) {
            super(minute, hour, day, second);
        }
    }
}
