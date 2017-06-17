package org.skywalking.apm.collector.worker.instance;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.skywalking.apm.collector.worker.config.EsConfig;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;

public class InstanceIndex extends AbstractIndex {

    public static final String INDEX = "instances";

    public static final String APPLICATION_CODE = "ac";
    public static final String REGISTRY_TIME = "rt";
    public static final String PING_TIME = "pt";
    public static final String INSTANCE_COUNT = "count";

    @Override
    public int refreshInterval() {
        return EsConfig.Es.Index.RefreshInterval.InstanceIndex.VALUE;
    }

    @Override
    public boolean isRecord() {
        return false;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("properties")
            .startObject("ac")
            .field("type", "keyword")
            .endObject()
            .startObject("ii")
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject("pt")
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject("rt")
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .endObject()
            .endObject();
        return mappingBuilder;
    }

    @Override
    public String index() {
        return INDEX;
    }
}
