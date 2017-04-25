package com.a.eye.skywalking.collector.worker.node;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertEquals("{\"properties\":{\"name\":{\"type\":\"keyword\"},\"peers\":{\"type\":\"keyword\"},\"aggId\":{\"type\":\"keyword\"}}}", index.createMappingBuilder().string());
    }
}
