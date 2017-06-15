package org.skywalking.apm.collector.worker.segment;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.worker.config.EsConfig;

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
        Assert.assertEquals("{\"properties\":{\"segId\":{\"type\":\"keyword\"},\"startTime\":{\"type\":\"long\",\"index\":\"not_analyzed\"},\"endTime\":{\"type\":\"long\",\"index\":\"not_analyzed\"},\"globalTraceId\":{\"type\":\"keyword\"},\"operationName\":{\"type\":\"text\"},\"cost\":{\"type\":\"long\",\"index\":\"not_analyzed\"}}}", index.createMappingBuilder().string());
    }

    @Test
    public void refreshInterval() {
        SegmentCostIndex index = new SegmentCostIndex();
        Assert.assertEquals(EsConfig.Es.Index.RefreshInterval.SegmentCostIndex.VALUE.intValue(), index.refreshInterval());
    }
}
