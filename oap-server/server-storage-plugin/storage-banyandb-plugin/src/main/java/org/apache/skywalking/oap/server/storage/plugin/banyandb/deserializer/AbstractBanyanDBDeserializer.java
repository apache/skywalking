package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;

import java.util.Collections;
import java.util.List;

public abstract class AbstractBanyanDBDeserializer<T> implements RowEntityMapper<T> {
    private final String indexName;
    private final List<String> searchableProjection;
    private final List<String> dataProjection;

    protected AbstractBanyanDBDeserializer(String indexName, List<String> searchableProjection) {
        this(indexName, searchableProjection, Collections.emptyList());
    }

    protected AbstractBanyanDBDeserializer(String indexName, List<String> searchableProjection, List<String> dataProjection) {
        this.indexName = indexName;
        this.searchableProjection = searchableProjection;
        this.dataProjection = dataProjection;
    }

    public StreamQuery buildStreamQuery() {
        final StreamQuery query = new StreamQuery(this.indexName, this.searchableProjection);
        query.setDataProjections(this.dataProjection);
        return query;
    }

    public StreamQuery buildStreamQuery(long startTimestamp, long endTimestamp) {
        final StreamQuery query = new StreamQuery(this.indexName, new TimestampRange(startTimestamp, endTimestamp), this.searchableProjection);
        query.setDataProjections(this.dataProjection);
        return query;
    }
}
