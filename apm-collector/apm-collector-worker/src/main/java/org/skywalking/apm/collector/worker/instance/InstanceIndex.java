package org.skywalking.apm.collector.worker.instance;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.skywalking.apm.collector.worker.config.EsConfig;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;

public class InstanceIndex extends AbstractIndex {

    public static final String INDEX = "instance_idx";

    public static final String APPLICATION_CODE = "ac";
    public static final String REGISTRY_TIME = "rt";
    public static final String INSTANCE_ID = "ii";

    public static final String TYPE_REGISTRY = "registry";

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
            .startObject(APPLICATION_CODE)
            .field("type", "keyword")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(INSTANCE_ID)
            .field("type", "keyword")
            .field("index", "not_analyzed")
            .endObject()
            .startObject(REGISTRY_TIME)
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
