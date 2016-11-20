package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.storage.data.index.IndexMetaCollection;

public interface IndexOperator {
    void batchUpdate(IndexMetaCollection metaInfos);

    IndexMetaCollection findIndex(Long[] traceId);
}
