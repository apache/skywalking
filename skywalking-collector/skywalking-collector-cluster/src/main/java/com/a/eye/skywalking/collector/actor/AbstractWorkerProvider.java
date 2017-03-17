package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractWorkerProvider<T extends AbstractWorker> implements Provider {

    public abstract Role role();

    public abstract T workerInstance(ClusterWorkerContext clusterContext);

    public abstract WorkerRef onCreate(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFountException;

    final public WorkerRef create(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFountException {
        if (workerInstance(clusterContext) == null) {
            throw new IllegalArgumentException("cannot get worker instance with nothing obtained from workerInstance()");
        }
        return onCreate(clusterContext, localContext);
    }
}
