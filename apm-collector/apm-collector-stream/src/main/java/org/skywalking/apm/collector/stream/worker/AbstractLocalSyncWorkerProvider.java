package org.skywalking.apm.collector.stream.worker;

/**
 * @author pengys5
 */
public abstract class AbstractLocalSyncWorkerProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalWorkerProvider<T> {

    @Override final public WorkerRef create() throws ProviderNotFoundException {
        T localSyncWorker = workerInstance(getClusterContext());
        localSyncWorker.preStart();

        LocalSyncWorkerRef workerRef = new LocalSyncWorkerRef(role(), localSyncWorker);
        return workerRef;
    }
}
