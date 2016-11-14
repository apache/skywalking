package com.a.eye.skywalking.storage.data.index;

import com.a.eye.skywalking.storage.data.IndexDataCapacityMonitor;
import com.a.eye.skywalking.storage.data.exception.IndexMetaStoredFailedException;

public class IndexOperator {

    private IndexDBConnector connector;
    private long             timestamp;

    private IndexOperator(IndexDBConnector connector) {
        this.connector = connector;
        timestamp = connector.getTimestamp();
    }

    public void batchUpdate(IndexMetaGroup metaGroup) {
        try {
            connector.batchUpdate(metaGroup);
            IndexDataCapacityMonitor.addIndexData(timestamp, metaGroup.size());
        } catch (Exception e) {
            throw new IndexMetaStoredFailedException("Failed to batch save index meta", e);
        }
    }

    public static IndexOperator newOperator(IndexDBConnector indexDBConnector) {
        return new IndexOperator(indexDBConnector);
    }
}
