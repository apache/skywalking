package com.a.eye.skywalking.collector.worker.noderef;

import com.a.eye.skywalking.collector.worker.storage.index.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeRefResSumIndex extends AbstractIndex {

    public static final String Index = "node_ref_res_sum_idx";
    public static final String OneSecondLess = "oneSecondLess";
    public static final String ThreeSecondLess = "threeSecondLess";
    public static final String FiveSecondLess = "fiveSecondLess";
    public static final String FiveSecondGreater = "fiveSecondGreater";
    public static final String Error = "error";
    public static final String Summary = "summary";

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
                        .startObject(OneSecondLess)
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(ThreeSecondLess)
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(FiveSecondLess)
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(FiveSecondGreater)
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(Error)
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(Summary)
                            .field("type", "long")
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
