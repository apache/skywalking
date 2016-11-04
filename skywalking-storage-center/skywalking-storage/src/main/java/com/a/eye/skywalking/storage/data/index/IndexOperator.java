package com.a.eye.skywalking.storage.data.index;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static com.a.eye.skywalking.storage.config.Config.DataIndex.TABLE_NAME;

public class IndexOperator {

    private Connection connection;
    private long       timestamp;

    private IndexOperator(long timestamp) {

    }

    public List<IndexMetaInfo> find(String taceId) {
        return new ArrayList<IndexMetaInfo>();
    }


    public void update(List<IndexMetaInfo> metaInfo) {

    }

    private Connection getConnection() {
        return connection;
    }

    public static class Builder {
        private IndexOperator       operator;
        private IndexOperatorHelper indexOperatorHelper;

        private Builder(long timestamp) {
            operator = new IndexOperator(timestamp);
            indexOperatorHelper = new IndexOperatorHelper(operator.getConnection());
        }

        public static Builder newBuilder(long timestamp) {
            return new Builder(timestamp);
        }

        public IndexOperator build() {
            if (indexOperatorHelper.validateIsReady(TABLE_NAME)) {
                indexOperatorHelper.maintain();
            }

            return operator;
        }

    }

}
