package org.skywalking.apm.collector.worker.instance;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;

public class InstanceInfoIndex extends AbstractIndex {

    public static final String TYPE = "instances";

    public static final String INDEX = "detail";

    @Override
    public int refreshInterval() {
        return 10;
    }

    @Override
    public boolean isRecord() {
        return true;
    }

    @Override
    public XContentBuilder createMappingBuilder() throws IOException {
        return XContentFactory.jsonBuilder()
            .startObject()
            .startObject("properties")
            .startObject("ac")
            .field("type", "string")
            .endObject()
            .startObject("ii")
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject("rt")
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .startObject("pt")
            .field("type", "long")
            .field("index", "not_analyzed")
            .endObject()
            .endObject()
            .endObject();
    }

    @Override
    public String index() {
        return INDEX;
    }
}
