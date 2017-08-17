package org.skywalking.apm.collector.stream.worker;

/**
 * @author pengys5
 */
public abstract class AbstractWorkerProvider<T extends AbstractWorker> implements Provider {

    private ClusterWorkerContext clusterContext;

    public abstract Role role();

    public abstract T workerInstance(ClusterWorkerContext clusterContext);

    final public void setClusterContext(ClusterWorkerContext clusterContext) {
        this.clusterContext = clusterContext;
    }

    final protected ClusterWorkerContext getClusterContext() {
        return clusterContext;
    }
}
