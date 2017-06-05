package org.skywalking.apm.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 * @author pengys5
 */
public class SegmentPersistenceDataTestCase {

    @Test
    public void getOrCreate() throws NoSuchFieldException, IllegalAccessException {
        SegmentPersistenceData segmentPersistenceData = new SegmentPersistenceData();
        segmentPersistenceData.hold();
        SegmentData segmentData = segmentPersistenceData.getOrCreate("Test1");

        SegmentData segmentData_1 = segmentPersistenceData.getOrCreate("Test1");
        Assert.assertEquals(segmentData, segmentData_1);

        SegmentData segmentData_2 = segmentPersistenceData.getOrCreate("Test2");
        Assert.assertEquals(2, segmentPersistenceData.size());

        System.out.println(segmentPersistenceData.asMap().toString());
        Assert.assertEquals(segmentData, segmentPersistenceData.asMap().get("Test1"));
        Assert.assertEquals(segmentData_2, segmentPersistenceData.asMap().get("Test2"));

        Field testAField = segmentPersistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<SegmentData> windowData = (WindowData<SegmentData>) testAField.get(segmentPersistenceData);
        Assert.assertEquals(true, windowData.isHolding());

        segmentPersistenceData.release();
        Assert.assertEquals(false, windowData.isHolding());
    }
}
