package com.a.eye.skywalking.storage.data.index.operator;


import com.a.eye.skywalking.storage.data.index.IndexMetaCollection;

/**
 * Created by xin on 2016/11/20.
 */
public class FinderExecutor implements Executor<IndexMetaCollection> {

    private Long[] traceIdSegment;

    public FinderExecutor(Long[] traceIdSegment) {
        this.traceIdSegment = traceIdSegment;
    }

    @Override
    public com.a.eye.skywalking.storage.data.index.IndexMetaCollection execute(IndexOperator indexOperator) {
        return indexOperator.findIndex(traceIdSegment);
    }
}
