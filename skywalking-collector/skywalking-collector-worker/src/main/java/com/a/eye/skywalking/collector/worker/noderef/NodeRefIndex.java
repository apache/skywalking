package com.a.eye.skywalking.collector.worker.noderef;

import com.a.eye.skywalking.collector.worker.config.EsConfig;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * @author pengys5
 */
public class NodeRefIndex extends AbstractIndex {
    public static final String INDEX = "node_ref_idx";
    public static final String FRONT = "front";
    public static final String FRONT_IS_REAL_CODE = "frontIsRealCode";
    public static final String BEHIND = "behind";
    public static final String BEHIND_IS_REAL_CODE = "behindIsRealCode";

    @Override
    public String index() {
        return INDEX;
    }

    @Override
    public boolean isRecord() {
        return false;
    }

    @Override
    public int refreshInterval() {
        return EsConfig.Es.Index.RefreshInterval.NodeRefIndex.VALUE;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject(FRONT)
                .field("type", "string")
                .field("index", "not_analyzed")
                .endObject()
                .startObject(FRONT_IS_REAL_CODE)
                .field("type", "boolean")
                .field("index", "not_analyzed")
                .endObject()
                .startObject(BEHIND)
                .field("type", "string")
                .field("index", "not_analyzed")
                .endObject()
                .startObject(BEHIND_IS_REAL_CODE)
                .field("type", "boolean")
                .field("index", "not_analyzed")
                .endObject()
                .startObject(AGG_COLUMN)
                .field("type", "string")
                .field("index", "not_analyzed")
                .endObject()
                .startObject(TIME_SLICE)
                .field("type", "long")
                .field("index", "not_analyzed")
                .endObject()
                .endObject()
                .endObject();
        return mappingBuilder;
    }
}
