package org.skywalking.apm.collector.worker.segment;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.worker.config.EsConfig;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentExceptionIndexTestCase {

    @Test
    public void test() {
        SegmentExceptionIndex index = new SegmentExceptionIndex();
        Assert.assertEquals("segment_exp_idx", index.index());
        Assert.assertEquals(true, index.isRecord());
    }

    @Test
    public void testBuilder() throws IOException {
        SegmentExceptionIndex index = new SegmentExceptionIndex();
        Assert.assertEquals("{\"properties\":{\"segId\":{\"type\":\"keyword\"},\"isError\":{\"type\":\"boolean\",\"index\":\"not_analyzed\"},\"errorKind\":{\"type\":\"keyword\"}}}", index.createMappingBuilder().string());
    }

    @Test
    public void refreshInterval() {
        SegmentExceptionIndex index = new SegmentExceptionIndex();
        Assert.assertEquals(EsConfig.Es.Index.RefreshInterval.SegmentExceptionIndex.VALUE.intValue(), index.refreshInterval());
    }
}
