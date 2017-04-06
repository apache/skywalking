package com.a.eye.skywalking.collector.worker.globaltrace;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * @author pengys5
 */
public class GlobalTraceIndexTestCase {

    @Test
    public void test() {
        GlobalTraceIndex index = new GlobalTraceIndex();
        Assert.assertEquals("global_trace_idx", index.index());
        Assert.assertEquals(true, index.isRecord());
    }

    @Test
    public void testBuilder() throws IOException {
        GlobalTraceIndex index = new GlobalTraceIndex();
        Assert.assertEquals("{\"properties\":{\"subSegIds\":{\"type\":\"text\",\"index\":\"not_analyzed\"}}}", index.createMappingBuilder().string());
    }
}
