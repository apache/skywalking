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
        Assert.assertEquals("{\"properties\":{\"front\":{\"type\":\"string\",\"index\":\"not_analyzed\"},\"frontIsRealCode\":{\"type\":\"boolean\",\"index\":\"not_analyzed\"},\"behind\":{\"type\":\"string\",\"index\":\"not_analyzed\"},\"behindIsRealCode\":{\"type\":\"boolean\",\"index\":\"not_analyzed\"},\"aggId\":{\"type\":\"string\",\"index\":\"not_analyzed\"},\"timeSlice\":{\"type\":\"long\",\"index\":\"not_analyzed\"}}}", index.createMappingBuilder().string());
    }
}
