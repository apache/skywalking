package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentIndex extends AbstractIndex {

    private Logger logger = LogManager.getFormatterLogger(SegmentIndex.class);

    public static final String Index = "segment_idx";

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
                        .startObject("traceSegmentId")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("startTime")
                            .field("type", "date")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("endTime")
                            .field("type", "date")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("applicationCode")
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("minute")
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("hour")
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject("day")
                            .field("type", "long")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startArray("refs")
                            .startObject("traceSegmentId")
                                .field("type", "String")
                                .field("index", "not_analyzed")
                            .endObject()
                            .startObject("spanId")
                                .field("type", "integer")
                                .field("index", "not_analyzed")
                            .endObject()
                            .startObject("applicationCode")
                                .field("type", "String")
                                .field("index", "not_analyzed")
                            .endObject()
                            .startObject("peerHost")
                                .field("type", "String")
                                .field("index", "not_analyzed")
                            .endObject()
                        .endArray()
                        .startArray("refs")
                            .startObject("spanId")
                                .field("type", "integer")
                                .field("index", "not_analyzed")
                            .endObject()
                            .startObject("parentSpanId")
                                .field("type", "integer")
                                .field("index", "not_analyzed")
                            .endObject()
                            .startObject("startTime")
                                .field("type", "date")
                                .field("index", "not_analyzed")
                            .endObject()
                            .startObject("endTime")
                                .field("type", "date")
                                .field("index", "not_analyzed")
                            .endObject()
                            .startObject("operationName")
                                .field("type", "String")
                                .field("index", "not_analyzed")
                            .endObject()
                        .endArray()
                        .startArray("relatedGlobalTraces")
                            .startObject("id")
                                .field("type", "String")
                                .field("index", "not_analyzed")
                            .endObject()
                        .endArray()
                    .endObject()
                .endObject();
        return mappingBuilder;
    }
}
