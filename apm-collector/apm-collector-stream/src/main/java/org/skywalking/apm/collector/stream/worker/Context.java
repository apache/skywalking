package org.skywalking.apm.collector.stream.worker;

/**
 * @author pengys5
 */
public interface Context extends LookUp {

    void putProvider(AbstractRemoteWorkerProvider provider);

    WorkerRefs lookup(Role role) throws WorkerNotFoundException;

    RemoteWorkerRef lookupInSide(String roleName) throws WorkerNotFoundException;

    void put(WorkerRef workerRef);

    void remove(WorkerRef workerRef);
}
