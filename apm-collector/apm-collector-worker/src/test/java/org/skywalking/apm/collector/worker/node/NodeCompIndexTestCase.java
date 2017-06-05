package org.skywalking.apm.collector.worker.node;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.worker.config.EsConfig;

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
        Assert.assertEquals("{\"properties\":{\"name\":{\"type\":\"keyword\"},\"peers\":{\"type\":\"keyword\"},\"aggId\":{\"type\":\"keyword\"}}}", index.createMappingBuilder().string());
    }

    @Test
    public void refreshInterval() {
        NodeCompIndex index = new NodeCompIndex();
        Assert.assertEquals(EsConfig.Es.Index.RefreshInterval.NodeCompIndex.VALUE.intValue(), index.refreshInterval());
    }
}
