package com.a.eye.skywalking.collector.worker.noderef;

import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeRefIndexTestCase {

    @Test
    public void test() {
        NodeRefIndex index = new NodeRefIndex();
        Assert.assertEquals("node_ref_idx", index.index());
        Assert.assertEquals(false, index.isRecord());
    }

    @Test
    public void testBuilder() throws IOException {
        NodeRefIndex index = new NodeRefIndex();
        Assert.assertEquals("{\"properties\":{\"front\":{\"type\":\"keyword\"},\"frontIsRealCode\":{\"type\":\"boolean\",\"index\":\"not_analyzed\"},\"behind\":{\"type\":\"keyword\"},\"behindIsRealCode\":{\"type\":\"boolean\",\"index\":\"not_analyzed\"},\"aggId\":{\"type\":\"keyword\"},\"timeSlice\":{\"type\":\"long\",\"index\":\"not_analyzed\"}}}", index.createMappingBuilder().string());
    }
}
