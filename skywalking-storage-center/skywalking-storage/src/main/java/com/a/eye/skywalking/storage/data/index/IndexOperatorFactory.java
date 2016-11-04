package com.a.eye.skywalking.storage.data.index;

/**
 * Created by xin on 2016/11/4.
 */
public class IndexOperatorFactory {

    private static IndexOperatorCache operatorCache;

    public static IndexOperator getIndexOperator(long timestamp) {
        IndexOperator operator = operatorCache.get(timestamp);

        if (operator == null) {
            operator = IndexOperator.Builder.newBuilder(timestamp).build();
            operatorCache.updateCache(timestamp, operator);
        }

        return operator;
    }
}
