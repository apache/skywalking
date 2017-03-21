package com.a.eye.skywalking.collector.worker.noderef;

import com.a.eye.skywalking.collector.worker.storage.index.AbstractIndex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeRefResSumIndex extends AbstractIndex {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumIndex.class);

    public static final String Index = "node_ref_res_sum_idx";

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
                        .startObject("front")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("behind")
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
