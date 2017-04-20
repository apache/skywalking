package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentCostIndexTestCase {

    @Test
    public void test() {
        SegmentCostIndex index = new SegmentCostIndex();
        Assert.assertEquals("segment_cost_idx", index.index());
        Assert.assertEquals(true, index.isRecord());
    }

    @Test
    public void testBuilder() throws IOException {
        SegmentCostIndex index = new SegmentCostIndex();
        Assert.assertEquals("{\"properties\":{\"segId\":{\"type\":\"string\",\"index\":\"not_analyzed\"},\"startTime\":{\"type\":\"long\",\"index\":\"not_analyzed\"},\"END_TIME\":{\"type\":\"long\",\"index\":\"not_analyzed\"},\"operationName\":{\"type\":\"string\",\"index\":\"not_analyzed\"},\"cost\":{\"type\":\"long\",\"index\":\"not_analyzed\"}}}", index.createMappingBuilder().string());
    }
}
