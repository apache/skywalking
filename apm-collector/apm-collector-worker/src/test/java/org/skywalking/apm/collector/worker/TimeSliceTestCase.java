package org.skywalking.apm.collector.worker;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class TimeSliceTestCase {

    @Test
    public void test() {
        TestTimeSlice timeSlice = new TestTimeSlice("A", 10L, 20L);
        Assert.assertEquals("A", timeSlice.getSliceType());
        Assert.assertEquals(10L, timeSlice.getStartTime());
        Assert.assertEquals(20L, timeSlice.getEndTime());
    }

    class TestTimeSlice extends TimeSlice {
        public TestTimeSlice(String sliceType, long startTime, long endTime) {
            super(sliceType, startTime, endTime);
        }
    }
}
