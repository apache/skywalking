package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractWorkerProvider<T extends AbstractWorker> implements Provider {

    private ClusterWorkerContext clusterContext;

    public abstract Role role();

    public abstract T workerInstance(ClusterWorkerContext clusterContext);

    public abstract WorkerRef onCreate(LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFoundException;

    final public void setClusterContext(ClusterWorkerContext clusterContext) {
        this.clusterContext = clusterContext;
    }

    final protected ClusterWorkerContext getClusterContext() {
        return clusterContext;
    }

    final public WorkerRef create(AbstractWorker workerOwner) throws IllegalArgumentException, ProviderNotFoundException {
        if (workerInstance(clusterContext) == null) {
            throw new IllegalArgumentException("cannot get worker instance with nothing obtained from workerInstance()");
        }

        if (workerOwner == null) {
            return onCreate(null);
        } else if (workerOwner.getSelfContext() instanceof LocalWorkerContext) {
            return onCreate((LocalWorkerContext) workerOwner.getSelfContext());
        } else {
            throw new IllegalArgumentException("the argument of workerOwner is Illegal");
        }
    }
}
