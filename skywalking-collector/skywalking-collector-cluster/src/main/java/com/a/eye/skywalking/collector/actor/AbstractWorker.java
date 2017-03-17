package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractWorker {
    private final LocalWorkerContext selfContext;

    private final Role role;

    private final ClusterWorkerContext clusterContext;

    public AbstractWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        this.role = role;
        this.clusterContext = clusterContext;
        this.selfContext = selfContext;
    }

    public abstract void preStart() throws ProviderNotFountException;

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
