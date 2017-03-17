package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalWorker extends AbstractWorker {
    public AbstractLocalWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }
}
