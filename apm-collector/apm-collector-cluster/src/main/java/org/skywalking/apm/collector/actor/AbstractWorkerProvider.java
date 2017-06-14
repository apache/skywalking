package org.skywalking.apm.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractWorkerProvider<T extends AbstractWorker> implements Provider {

    private ClusterWorkerContext clusterContext;

    public abstract Role role();

    public abstract T workerInstance(ClusterWorkerContext clusterContext);

    public abstract WorkerRef onCreate(
        LocalWorkerContext localContext) throws ProviderNotFoundException;

    final public void setClusterContext(ClusterWorkerContext clusterContext) {
        this.clusterContext = clusterContext;
    }

    final protected ClusterWorkerContext getClusterContext() {
        return clusterContext;
    }

    final public WorkerRef create(
        AbstractWorker workerOwner) throws ProviderNotFoundException {

        if (workerOwner == null) {
            return onCreate(null);
        } else if (workerOwner.getSelfContext() instanceof LocalWorkerContext) {
            return onCreate((LocalWorkerContext)workerOwner.getSelfContext());
        } else {
            throw new IllegalArgumentException("the argument of workerOwner is Illegal");
        }
    }
}
