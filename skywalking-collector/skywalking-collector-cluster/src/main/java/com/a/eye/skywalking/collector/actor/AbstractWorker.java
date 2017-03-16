package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractWorker {

    private final LocalWorkerContext selfContext = new LocalWorkerContext();

    private final Role role;

    private final ClusterWorkerContext clusterContext;

    public AbstractWorker(Role role, ClusterWorkerContext clusterContext) {
        this.role = role;
        this.clusterContext = clusterContext;
    }

    public abstract void preStart() throws Exception;

    public abstract void work(Object message) throws Exception;

    final public LocalWorkerContext getSelfContext() {
        return selfContext;
    }

    final public ClusterWorkerContext getClusterContext() {
        return clusterContext;
    }

    final public Role getRole() {
        return role;
    }
}
