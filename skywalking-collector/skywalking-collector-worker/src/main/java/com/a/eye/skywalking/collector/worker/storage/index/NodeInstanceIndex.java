package com.a.eye.skywalking.collector.worker.storage.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeInstanceIndex extends AbstractIndex {

    private static Logger logger = LogManager.getFormatterLogger(NodeInstanceIndex.class);

    public static final String Index = "node_instance_idx";

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
                        .startObject("address")
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
