package com.a.eye.skywalking.storage.data.index.operator;

interface Executor<T> {
    T execute(IndexOperator indexOperator);
}
