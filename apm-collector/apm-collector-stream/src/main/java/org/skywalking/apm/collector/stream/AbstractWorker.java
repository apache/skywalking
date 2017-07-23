package org.skywalking.apm.collector.stream;

import org.skywalking.apm.collector.core.framework.Executor;

/**
 * @author pengys5
 */
public abstract class AbstractWorker implements Executor {

    private final LocalWorkerContext selfContext;

    private final Role role;

    private final ClusterWorkerContext clusterContext;

    public AbstractWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        this.role = role;
        this.clusterContext = clusterContext;
        this.selfContext = selfContext;
    }

    @Override public final void execute(Object message) {

    }

    public abstract void preStart() throws ProviderNotFoundException;

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
