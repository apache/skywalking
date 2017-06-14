package org.skywalking.apm.collector.actor;

/**
 * The <code>AbstractLocalSyncWorker</code> defines workers who receive data from jvm inside call and response in real
 * time.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractLocalSyncWorker extends AbstractLocalWorker {
    public AbstractLocalSyncWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * Called by the worker reference to execute the worker service.
     *
     * @param request {@link Object} is an input parameter
     * @param response {@link Object} is an output parameter
     */
    final public void allocateJob(Object request, Object response) throws WorkerInvokeException {
        try {
            onWork(request, response);
        } catch (WorkerException e) {
            throw new WorkerInvokeException(e.getMessage(), e.getCause());
        }
    }

    /**
     * Override this method to implement business logic.
     *
     * @param request {@link Object} is a in parameter
     * @param response {@link Object} is a out parameter
     */
    protected abstract void onWork(Object request, Object response) throws WorkerException;

    /**
     * Prepare methods before this work starts to work.
     * <p>Usually, create or find the workers reference should be call.
     *
     * @throws ProviderNotFoundException
     */
    @Override
    public void preStart() throws ProviderNotFoundException {
    }
}
