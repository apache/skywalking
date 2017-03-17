package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalSyncWorkerProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalWorkerProvider<T> {

    @Override
    final public WorkerRef onCreate(LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFountException {
        T localSyncWorker = (T) workerInstance(getClusterContext());
        localSyncWorker.preStart();

        LocalSyncWorkerRef workerRef = new LocalSyncWorkerRef(role(), localSyncWorker);

        if (localContext != null) {
            localContext.put(workerRef);
        }
        return workerRef;
    }
}
