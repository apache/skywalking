package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.config.EsConfig;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class SegmentExceptionIndex extends AbstractIndex {
    public static final String INDEX = "segment_exp_idx";
    public static final String SEG_ID = "segId";
    public static final String IS_ERROR = "isError";
    public static final String ERROR_KIND = "errorKind";

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
        return EsConfig.Es.Index.RefreshInterval.SegmentExceptionIndex.VALUE;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject(SEG_ID)
                .field("type", "keyword")
                .endObject()
                .startObject(IS_ERROR)
                .field("type", "boolean")
                .field("index", "not_analyzed")
                .endObject()
                .startObject(ERROR_KIND)
                .field("type", "keyword")
                .endObject()
                .endObject()
                .endObject();
        return mappingBuilder;
    }
}
