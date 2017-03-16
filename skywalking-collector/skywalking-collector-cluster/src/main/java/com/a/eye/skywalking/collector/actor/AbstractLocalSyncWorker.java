package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalSyncWorker extends AbstractLocalWorker {
    public AbstractLocalSyncWorker(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }
}
