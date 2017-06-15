package org.skywalking.apm.collector.worker.instance;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.skywalking.apm.collector.worker.config.EsConfig;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;

public class PingTimeIndex extends AbstractIndex {

    public static final String INDEX = "instance_idx";

    public static final String TYPE_PING_TIME = "ping_time";
    public static final String PING_TIME = "pt";
    public static final String INSTANCE_ID = "ii";

    @Override public int refreshInterval() {
        return EsConfig.Es.Index.RefreshInterval.PingTime.VALUE;
    }

    @Override public boolean isRecord() {
        return false;
    }

    @Override public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("properties")
            .startObject(INSTANCE_ID)
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(PING_TIME)
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .endObject()
            .endObject();
        return mappingBuilder;
    }

    @Override public String index() {
        return INDEX;
    }
}
