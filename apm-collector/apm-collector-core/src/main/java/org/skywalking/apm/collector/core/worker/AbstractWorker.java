package org.skywalking.apm.collector.core.worker;

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

    public abstract void preStart() throws ProviderNotFoundException;

    final public LookUp getSelfContext() {
        return selfContext;
    }

    final public LookUp getClusterContext() {
        return clusterContext;
    }

    final public Role getRole() {
        return role;
    }

    final public static AbstractWorker noOwner() {
        return null;
    }
}
