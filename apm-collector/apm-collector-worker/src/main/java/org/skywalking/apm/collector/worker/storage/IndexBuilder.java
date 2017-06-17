package org.skywalking.apm.collector.worker.storage;

import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;

public class IndexBuilder {
    private List<IndexRequestBuilder> indexRequestBuilders;
    private List<UpdateRequestBuilder> updateRequestBuilders;

    public IndexBuilder() {
        this.indexRequestBuilders = new ArrayList<>();
        this.updateRequestBuilders = new ArrayList<>();
    }

    public List<IndexRequestBuilder> getIndexRequestBuilders() {
        return indexRequestBuilders;
    }

    public List<UpdateRequestBuilder> getUpdateRequestBuilders() {
        return updateRequestBuilders;
    }

    public void addIndexRequestBuilder(IndexRequestBuilder builder) {
        this.indexRequestBuilders.add(builder);
    }
}
