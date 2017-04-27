package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.config.EsConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentIndexTestCase {

    @Test
    public void test() {
        SegmentIndex index = new SegmentIndex();
        Assert.assertEquals("segment_idx", index.index());
        Assert.assertEquals(true, index.isRecord());
    }

    @Test
    public void testBuilder() throws IOException {
        SegmentIndex index = new SegmentIndex();
        Assert.assertEquals("{\"properties\":{\"traceSegmentId\":{\"type\":\"keyword\"},\"startTime\":{\"type\":\"date\",\"index\":\"not_analyzed\"},\"endTime\":{\"type\":\"date\",\"index\":\"not_analyzed\"},\"applicationCode\":{\"type\":\"keyword\"},\"minute\":{\"type\":\"long\",\"index\":\"not_analyzed\"},\"hour\":{\"type\":\"long\",\"index\":\"not_analyzed\"},\"day\":{\"type\":\"long\",\"index\":\"not_analyzed\"}}}", index.createMappingBuilder().string());
    }

    @Test
    public void refreshInterval() {
        SegmentIndex index = new SegmentIndex();
        Assert.assertEquals(EsConfig.Es.Index.RefreshInterval.SegmentIndex.VALUE.intValue(), index.refreshInterval());
    }
}
