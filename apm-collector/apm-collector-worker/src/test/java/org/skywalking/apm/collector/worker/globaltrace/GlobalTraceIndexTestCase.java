package org.skywalking.apm.collector.worker.globaltrace;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.worker.config.EsConfig;

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
        Assert.assertEquals("{\"properties\":{\"subSegIds\":{\"type\":\"keyword\"}}}", index.createMappingBuilder().string());
    }

    @Test
    public void refreshInterval() {
        GlobalTraceIndex index = new GlobalTraceIndex();
        Assert.assertEquals(EsConfig.Es.Index.RefreshInterval.GlobalTraceIndex.VALUE.intValue(), index.refreshInterval());
    }
}
