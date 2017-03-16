package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public interface Context {

    AbstractWorkerProvider findProvider(Role role) throws ProviderNotFountException;

    void putProvider(AbstractWorkerProvider provider) throws DuplicateProviderException;

    WorkerRefs lookup(Role role) throws WorkerNotFountException;

    void put(WorkerRef workerRef);

    void remove(WorkerRef workerRef);
}
