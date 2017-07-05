package org.skywalking.apm.collector.worker.segment;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.skywalking.apm.collector.worker.config.EsConfig;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;

/**
 * @author pengys5
 */
public class SegmentIndex extends AbstractIndex {

    public static final String INDEX = "segment_idx";
    public static final String TRACE_SEGMENT_ID = "traceSegmentId";
    public static final String SEGMENT_OBJ_BLOB = "segmentObjBlob";

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
            .startObject(TRACE_SEGMENT_ID)
            .field("type", "keyword")
            .endObject()
            .startObject(TYPE_MINUTE)
            .field("type", "long")
            .endObject()
            .startObject(TYPE_HOUR)
            .field("type", "long")
            .endObject()
            .startObject(TYPE_DAY)
            .field("type", "long")
            .endObject()
            .startObject(SEGMENT_OBJ_BLOB)
            .field("type", "binary")
            .endObject()
            .endObject()
            .endObject();
    }
}
