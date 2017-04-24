package com.a.eye.skywalking.collector.worker.node;

import com.a.eye.skywalking.collector.worker.config.EsConfig;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeCompIndex extends AbstractIndex {

    public static final String INDEX = "node_comp_idx";
    public static final String NAME = "name";
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
        return EsConfig.Es.Index.RefreshInterval.NodeCompIndex.VALUE;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject(NAME)
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
                .endObject()
                .endObject();
        return mappingBuilder;
    }
}
