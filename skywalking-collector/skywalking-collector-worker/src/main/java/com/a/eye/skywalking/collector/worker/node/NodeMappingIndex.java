package com.a.eye.skywalking.collector.worker.node;

import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeMappingIndex extends AbstractIndex {

    public static final String Index = "node_mapping_idx";

    public static final String Code = "code";
    public static final String Peers = "peers";

    @Override
    public String index() {
        return Index;
    }

    @Override
    public boolean isRecord() {
        return false;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("properties")
                        .startObject(Code)
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(Peers)
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(AGG_COLUMN)
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(Time_Slice)
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                    .endObject()
                .endObject();
        return mappingBuilder;
    }
}
