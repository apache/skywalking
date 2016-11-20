package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.storage.data.index.IndexMetaCollection;

public class UpdateExecutor implements Executor<Integer> {

    private IndexMetaCollection metaCollection;

    public UpdateExecutor(IndexMetaCollection metaCollection) {
        this.metaCollection = metaCollection;
    }

    @Override
    public Integer execute(IndexOperator indexOperator) {
        return indexOperator.batchUpdate(metaCollection);
    }
}
