package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import org.junit.Assert;
import org.junit.Test;

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
}
