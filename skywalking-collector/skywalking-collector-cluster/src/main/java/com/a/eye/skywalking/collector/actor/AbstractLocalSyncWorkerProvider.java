package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalSyncWorkerProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalWorkerProvider<T> {

    @Override
    final public WorkerRef onCreate(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFountException {
        T localSyncWorker = (T) workerInstance(clusterContext);
        localSyncWorker.preStart();

        LocalSyncWorkerRef workerRef = new LocalSyncWorkerRef(role(), localSyncWorker);
        localContext.put(workerRef);
        return workerRef;
    }
}
