package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public interface PersistenceData<T extends Data> {

    T getElseCreate(String id);

    void releaseData();

    void holdData();
}
