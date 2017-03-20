package com.a.eye.skywalking.collector.worker.storage.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class ServerNodeIndex extends AbstractIndex {

    private Logger logger = LogManager.getFormatterLogger(ServerNodeIndex.class);

    public static final String Index = "server_node_idx";


    @Override
    public String index() {
        return Index;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("properties")
                        .startObject("code")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("layer")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("component")
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
