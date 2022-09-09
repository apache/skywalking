package org.apache.skywalking.oap.server.core.query;

import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.junit.Assert;
import org.junit.Test;

public class DurationTest {

    @Test
    public void testConvertToTimeBucket() {
        Assert.assertEquals(20220908L, DurationUtils.INSTANCE.convertToTimeBucket(Step.DAY, "2022-09-08"));
        Assert.assertEquals(2022090810L, DurationUtils.INSTANCE.convertToTimeBucket(Step.HOUR, "2022-09-08 10"));
        Assert.assertEquals(202209081010L, DurationUtils.INSTANCE.convertToTimeBucket(Step.MINUTE, "2022-09-08 1010"));
        Assert.assertEquals(
            20220908101010L, DurationUtils.INSTANCE.convertToTimeBucket(Step.SECOND, "2022-09-08 101010"));
        try {
            DurationUtils.INSTANCE.convertToTimeBucket(Step.DAY, "2022-09-08 10");
            Assert.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testStartTimeDurationToSecondTimeBucket() {
        Assert.assertEquals(
            20220908000000L, DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.DAY, "2022-09-08"));
        Assert.assertEquals(
            20220908100000L, DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.HOUR, "2022-09-08 10"));
        Assert.assertEquals(
            20220908101000L,
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.MINUTE, "2022-09-08 1010")
        );
        Assert.assertEquals(
            20220908101010L,
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.SECOND, "2022-09-08 101010")
        );
        try {
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.HOUR, "2022-09-08 30");
            Assert.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testEndTimeDurationToSecondTimeBucket() {
        Assert.assertEquals(
            20220908235959L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.DAY, "2022-09-08"));
        Assert.assertEquals(
            20220908105959L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.HOUR, "2022-09-08 10"));
        Assert.assertEquals(
            20220908101059L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.MINUTE, "2022-09-08 1010"));
        Assert.assertEquals(
            20220908101010L,
            DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.SECOND, "2022-09-08 101010")
        );
        try {
            DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.HOUR, "2022-09-08 30");
            Assert.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testStartTimeToTimestamp() {
        Assert.assertEquals(1662566400000L, DurationUtils.INSTANCE.startTimeToTimestamp(Step.DAY, "2022-09-08"));
        Assert.assertEquals(1662602400000L, DurationUtils.INSTANCE.startTimeToTimestamp(Step.HOUR, "2022-09-08 10"));
        Assert.assertEquals(
            1662603000000L, DurationUtils.INSTANCE.startTimeToTimestamp(Step.MINUTE, "2022-09-08 1010"));
        Assert.assertEquals(
            1662603010000L, DurationUtils.INSTANCE.startTimeToTimestamp(Step.SECOND, "2022-09-08 101010"));
        try {
            DurationUtils.INSTANCE.startTimeToTimestamp(Step.HOUR, "2022-09-08 30");
            Assert.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testEndTimeToTimestamp() {
        Assert.assertEquals(1662652800000L, DurationUtils.INSTANCE.endTimeToTimestamp(Step.DAY, "2022-09-08"));
        Assert.assertEquals(1662606000000L, DurationUtils.INSTANCE.endTimeToTimestamp(Step.HOUR, "2022-09-08 10"));
        Assert.assertEquals(1662603060000L, DurationUtils.INSTANCE.endTimeToTimestamp(Step.MINUTE, "2022-09-08 1010"));
        Assert.assertEquals(
            1662603011000L, DurationUtils.INSTANCE.endTimeToTimestamp(Step.SECOND, "2022-09-08 101010"));
        try {
            DurationUtils.INSTANCE.endTimeToTimestamp(Step.HOUR, "2022-09-08 30");
            Assert.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

}
