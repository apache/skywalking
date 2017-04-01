package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentCostIndex extends AbstractIndex {

    public static final String Index = "segment_cost_idx";

    public static final String SegId = "segId";
    public static final String StartTime = "startTime";
    public static final String EndTime = "EndTime";
    public static final String OperationName = "operationName";
    public static final String Cost = "cost";

    @Override
    public String index() {
        return Index;
    }

    @Override
    public boolean isRecord() {
        return true;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("properties")
                        .startObject(SegId)
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(StartTime)
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(EndTime)
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(OperationName)
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                            .startObject(Cost)
                            .field("type", "long")
                        .field("index", "not_analyzed")
                        .endObject()
                    .endObject()
                .endObject();
        return mappingBuilder;
    }
}
