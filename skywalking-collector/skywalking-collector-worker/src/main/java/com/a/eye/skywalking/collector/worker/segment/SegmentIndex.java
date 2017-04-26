package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.config.EsConfig;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentIndex extends AbstractIndex {

    public static final String INDEX = "segment_idx";

    @Override
    public String index() {
        return INDEX;
    }

    @Override
    public boolean isRecord() {
        return true;
    }

    @Override
    public int refreshInterval() {
        return EsConfig.Es.Index.RefreshInterval.SegmentIndex.VALUE;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        return XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("traceSegmentId")
                .field("type", "keyword")
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
                .field("type", "keyword")
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
                .endObject()
                .endObject();
    }
}
