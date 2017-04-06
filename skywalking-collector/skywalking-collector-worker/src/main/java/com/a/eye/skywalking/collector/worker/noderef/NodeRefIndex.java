package com.a.eye.skywalking.collector.worker.noderef;

import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeRefIndex extends AbstractIndex {

    public static final String Index = "node_ref_idx";

    public static final String Front = "front";
    public static final String FrontIsRealCode = "frontIsRealCode";
    public static final String Behind = "behind";
    public static final String BehindIsRealCode = "behindIsRealCode";

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
                        .startObject(Front)
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(FrontIsRealCode)
                            .field("type", "boolean")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(Behind)
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(BehindIsRealCode)
                            .field("type", "boolean")
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
