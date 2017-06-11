package org.skywalking.apm.collector.actor;

/**
 * The <code>AbstractLocalSyncWorker</code> use to define workers that receive data from jvm inside call and the
 * workers response result in real time.
 *
 * <p> The implementation class is same as normal class, it make the framework be similar to the asynchronous
 * workers inside jvm and outside jvm.
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
     * @param request {@link Object} is a in parameter
     * @param response {@link Object} is a out parameter
     * @throws Exception
     */
    final public void allocateJob(Object request, Object response) throws Exception {
        onWork(request, response);
    }

    /**
     * Override this method to implementing business logic.
     *
     * @param request {@link Object} is a in parameter
     * @param response {@link Object} is a out parameter
     * @throws Exception
     */
    protected abstract void onWork(Object request, Object response) throws Exception;

    /**
     * Called by the worker on start.
     * <p>Usually, create or find the workers reference should be call.
     *
     * @throws ProviderNotFoundException
     */
    @Override
    public void preStart() throws ProviderNotFoundException {
    }
}
