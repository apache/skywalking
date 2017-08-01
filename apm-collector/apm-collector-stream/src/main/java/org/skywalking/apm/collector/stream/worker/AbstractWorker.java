package org.skywalking.apm.collector.stream.worker;

import org.skywalking.apm.collector.core.framework.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public abstract class AbstractWorker implements Executor {

    private final Logger logger = LoggerFactory.getLogger(AbstractWorker.class);

    private final Role role;

    private final ClusterWorkerContext clusterContext;

    public AbstractWorker(Role role, ClusterWorkerContext clusterContext) {
        this.role = role;
        this.clusterContext = clusterContext;
    }

    @Override public final void execute(Object message) {
        try {
            onWork(message);
        } catch (WorkerException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * The data process logic in this method.
     *
     * @param message Cast the message object to a expect subclass.
     * @throws WorkerException Don't handle the exception, throw it.
     */
    protected abstract void onWork(Object message) throws WorkerException;

    public abstract void preStart() throws ProviderNotFoundException;

    final public ClusterWorkerContext getClusterContext() {
        return clusterContext;
    }

    final public Role getRole() {
        return role;
    }
}
