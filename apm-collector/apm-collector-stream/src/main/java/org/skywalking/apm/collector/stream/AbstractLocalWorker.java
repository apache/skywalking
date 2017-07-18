package org.skywalking.apm.collector.stream;

/**
 * @author pengys5
 */
public abstract class AbstractLocalWorker extends AbstractWorker {
    public AbstractLocalWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }
}
