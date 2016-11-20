package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.storage.data.index.IndexMetaCollection;

/**
 * Created by xin on 2016/11/20.
 */
public class UpdateExecutor implements Executor<Integer> {

    private IndexMetaCollection metaCollection;

    public UpdateExecutor(IndexMetaCollection metaCollection) {
        this.metaCollection = metaCollection;
    }

    @Override
    public IndexMetaCollection execute(IndexOperator indexOperator) {
        return null;
    }
}
