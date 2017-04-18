package com.a.eye.skywalking.collector.worker.noderef;

import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeRefResSumIndex extends AbstractIndex {
    public static final String INDEX = "node_ref_res_sum_idx";
    public static final String ONE_SECOND_LESS = "oneSecondLess";
    public static final String THREE_SECOND_LESS = "threeSecondLess";
    public static final String FIVE_SECOND_LESS = "fiveSecondLess";
    public static final String FIVE_SECOND_GREATER = "fiveSecondGreater";
    public static final String ERROR = "error";
    public static final String SUMMARY = "summary";

    @Override
    public String index() {
        return INDEX;
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
            .startObject(ONE_SECOND_LESS)
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(THREE_SECOND_LESS)
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(FIVE_SECOND_LESS)
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(FIVE_SECOND_GREATER)
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(ERROR)
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(SUMMARY)
            .field("type", "long")
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
