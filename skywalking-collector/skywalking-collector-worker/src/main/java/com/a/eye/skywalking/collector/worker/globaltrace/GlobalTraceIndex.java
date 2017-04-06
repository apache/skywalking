package com.a.eye.skywalking.collector.worker.globaltrace;

import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class GlobalTraceIndex extends AbstractIndex {

    public static final String Index = "global_trace_idx";
    public static final String SubSegIds = "subSegIds";


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
                        .startObject(SubSegIds)
                            .field("type", "text")
                            .field("index", "not_analyzed")
                        .endObject()
                    .endObject()
                .endObject();
        return mappingBuilder;
    }
}
