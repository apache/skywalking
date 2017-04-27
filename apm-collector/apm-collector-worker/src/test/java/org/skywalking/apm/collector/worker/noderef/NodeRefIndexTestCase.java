package org.skywalking.apm.collector.worker.noderef;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.worker.config.EsConfig;

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

    @Test
    public void refreshInterval() {
        NodeRefIndex index = new NodeRefIndex();
        Assert.assertEquals(EsConfig.Es.Index.RefreshInterval.NodeRefIndex.VALUE.intValue(), index.refreshInterval());
    }
}
