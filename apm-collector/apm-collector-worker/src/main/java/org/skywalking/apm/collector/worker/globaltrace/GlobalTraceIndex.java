package org.skywalking.apm.collector.worker.globaltrace;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.skywalking.apm.collector.worker.config.EsConfig;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;

import java.io.IOException;

/**
 * @author pengys5
 */
public class GlobalTraceIndex extends AbstractIndex {

    public static final String INDEX = "global_trace_idx";
    public static final String SUB_SEG_IDS = "subSegIds";

    @Override
    public String index() {
        return INDEX;
    }

    @Override
    public boolean isRecord() {
        return true;
    }

    @Override
    public int refreshInterval() {
        return EsConfig.Es.Index.RefreshInterval.GlobalTraceIndex.VALUE;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("properties")
            .startObject(SUB_SEG_IDS)
            .field("type", "keyword")
            .endObject()
            .endObject()
            .endObject();
        return mappingBuilder;
    }
}
