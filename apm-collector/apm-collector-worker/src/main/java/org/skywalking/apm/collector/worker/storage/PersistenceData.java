package org.skywalking.apm.collector.worker.storage;

/**
 * @author pengys5
 */
public interface PersistenceData<T extends Data> {

    T getOrCreate(String id);

    void release();

    void hold();
}
