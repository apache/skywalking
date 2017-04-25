package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.config.EsConfig;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentCostIndex extends AbstractIndex {

    public static final String INDEX = "segment_cost_idx";
    public static final String SEG_ID = "segId";
    public static final String START_TIME = "startTime";
    public static final String END_TIME = "END_TIME";
    public static final String OPERATION_NAME = "operationName";
    public static final String COST = "cost";

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
        return EsConfig.Es.Index.RefreshInterval.SegmentCostIndex.VALUE;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        return XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject(SEG_ID)
                .field("type", "keyword")
                .endObject()
                .startObject(START_TIME)
                .field("type", "long")
                .field("index", "not_analyzed")
                .endObject()
                .startObject(END_TIME)
                .field("type", "long")
                .field("index", "not_analyzed")
                .endObject()
                .startObject(OPERATION_NAME)
                .field("type", "keyword")
                .endObject()
                .startObject(COST)
                .field("type", "long")
                .field("index", "not_analyzed")
                .endObject()
                .endObject()
                .endObject();
    }
}
