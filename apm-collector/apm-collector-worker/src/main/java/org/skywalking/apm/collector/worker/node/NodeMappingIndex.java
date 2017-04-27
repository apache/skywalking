package org.skywalking.apm.collector.worker.node;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.skywalking.apm.collector.worker.config.EsConfig;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeMappingIndex extends AbstractIndex {

    public static final String INDEX = "node_mapping_idx";
    public static final String CODE = "code";
    public static final String PEERS = "peers";

    @Override
    public String index() {
        return INDEX;
    }

    @Override
    public boolean isRecord() {
        return false;
    }

    @Override
    public int refreshInterval() {
        return EsConfig.Es.Index.RefreshInterval.NodeMappingIndex.VALUE;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("properties")
            .startObject(CODE)
            .field("type", "string")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(PEERS)
            .field("type", "string")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(AGG_COLUMN)
            .field("type", "string")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(TIME_SLICE)
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .endObject()
            .endObject();
        return mappingBuilder;
    }
}
