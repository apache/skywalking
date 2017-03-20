package com.a.eye.skywalking.collector.worker.storage.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class ClientNodeIndex extends AbstractIndex {

    private Logger logger = LogManager.getFormatterLogger(ClientNodeIndex.class);

    public static final String Index = "client_node_idx";

    @Override
    public String index() {
        return Index;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("properties")
                        .startObject("layer")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("component")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("serverHost")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("timeSlice")
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                    .endObject()
                .endObject();
        return mappingBuilder;
    }
}
