package com.a.eye.skywalking.collector.worker.node;

import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeCompIndexTestCase {

    @Test
    public void test() {
        NodeCompIndex index = new NodeCompIndex();
        Assert.assertEquals("node_comp_idx", index.index());
        Assert.assertEquals(false, index.isRecord());
    }

    @Test
    public void testBuilder() throws IOException {
        NodeCompIndex index = new NodeCompIndex();
        Assert.assertEquals("{\"properties\":{\"NAME\":{\"type\":\"string\",\"index\":\"not_analyzed\"},\"peers\":{\"type\":\"string\",\"index\":\"not_analyzed\"},\"aggId\":{\"type\":\"string\",\"index\":\"not_analyzed\"}}}", index.createMappingBuilder().string());
    }
}
