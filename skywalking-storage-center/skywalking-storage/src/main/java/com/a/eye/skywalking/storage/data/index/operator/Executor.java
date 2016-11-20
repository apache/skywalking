package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.storage.data.index.IndexMetaCollection;

interface Executor<T> {
    T execute(IndexOperator indexOperator);
}
