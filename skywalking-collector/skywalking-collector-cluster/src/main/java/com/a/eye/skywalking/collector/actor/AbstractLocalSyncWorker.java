package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalSyncWorker extends AbstractLocalWorker {
    public AbstractLocalSyncWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    final public void work(Object message) throws Exception {
    }

    public abstract Object onWork(Object message) throws Exception;
}
