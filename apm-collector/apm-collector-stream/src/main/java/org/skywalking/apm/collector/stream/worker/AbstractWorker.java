package org.skywalking.apm.collector.stream.worker;

import org.skywalking.apm.collector.core.framework.Executor;

/**
 * @author pengys5
 */
public abstract class AbstractWorker implements Executor {

    private final Role role;

    private final ClusterWorkerContext clusterContext;

    public AbstractWorker(Role role, ClusterWorkerContext clusterContext) {
        this.role = role;
        this.clusterContext = clusterContext;
    }

    @Override public final void execute(Object message) {

    }

    public abstract void preStart() throws ProviderNotFoundException;

    final public ClusterWorkerContext getClusterContext() {
        return clusterContext;
    }

    final public Role getRole() {
        return role;
    }
}
