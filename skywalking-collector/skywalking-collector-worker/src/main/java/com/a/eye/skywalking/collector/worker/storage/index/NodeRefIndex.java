package com.a.eye.skywalking.collector.worker.storage.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeRefIndex extends AbstractIndex {

    private Logger logger = LogManager.getFormatterLogger(NodeRefIndex.class);

    public static final String Index = "node_ref_idx";

    @Override
    public String index() {
        return Index;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("properties")
                        .startObject("client")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("server")
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
