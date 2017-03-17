package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public interface Context extends LookUp {

    void putProvider(AbstractWorkerProvider provider) throws UsedRoleNameException;

    void put(WorkerRef workerRef);

    void remove(WorkerRef workerRef);
}
