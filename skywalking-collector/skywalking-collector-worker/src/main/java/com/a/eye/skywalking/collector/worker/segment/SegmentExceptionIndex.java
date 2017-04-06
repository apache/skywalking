package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentExceptionIndex extends AbstractIndex {

    public static final String Index = "segment_exp_idx";

    public static final String SegId = "segId";
    public static final String IsError = "isError";
    public static final String ErrorKind = "errorKind";

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
                        .startObject(IsError)
                            .field("type", "boolean")
                            .field("index", "not_analyzed")
                        .endObject()
                        .startObject(ErrorKind)
                            .field("type", "string")
                            .field("index", "not_analyzed")
                        .endObject()
                    .endObject()
                .endObject();
        return mappingBuilder;
    }
}
