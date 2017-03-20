package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalSyncWorker extends AbstractLocalWorker {
    public AbstractLocalSyncWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final public void allocateJob(Object request, Object response) throws Exception {
        onWork(request, response);
    }

    protected abstract void onWork(Object request, Object response) throws Exception;

    @Override
    public void preStart() throws ProviderNotFoundException {
    }
}
