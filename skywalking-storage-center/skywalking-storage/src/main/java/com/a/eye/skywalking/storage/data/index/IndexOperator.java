package com.a.eye.skywalking.storage.data.index;

import java.util.ArrayList;
import java.util.List;

public class IndexOperator {

    private IndexDBConnector connector;
    private long             timestamp;

    private IndexOperator(IndexDBConnector connector) {
        this.connector = connector;
        timestamp = connector.getTimestamp();
    }

    public List<IndexMetaInfo> find(String taceId) {
        return new ArrayList<IndexMetaInfo>();
    }


    public void update(List<IndexMetaInfo> metaInfo) {

    }

    private IndexDBConnector getConnector() {
        return connector;
    }


    public static IndexOperator newOperator(long timestamp) {
        return newOperator(new IndexDBConnector(timestamp));
    }

    public static IndexOperator newOperator(IndexDBConnector indexDBConnector) {
        return new IndexOperator(indexDBConnector);
    }
}
