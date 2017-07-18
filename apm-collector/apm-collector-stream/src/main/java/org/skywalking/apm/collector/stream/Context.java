package org.skywalking.apm.collector.stream;

/**
 * @author pengys5
 */
public interface Context extends LookUp {

    void putProvider(AbstractWorkerProvider provider) throws UsedRoleNameException;

    WorkerRefs lookup(Role role) throws WorkerNotFoundException;

    void put(WorkerRef workerRef);

    void remove(WorkerRef workerRef);
}
