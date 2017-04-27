package com.a.eye.skywalking.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class SegmentDataTestCase {

    @Test
    public void test() {
        SegmentData segmentData = new SegmentData("Test1");

        segmentData.merge(null);
        Assert.assertEquals("Test1", segmentData.getId());

        segmentData.setSegmentStr("Test2");
        Assert.assertEquals("Test2", segmentData.getSegmentStr());
    }
}
