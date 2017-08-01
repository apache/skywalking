package org.skywalking.apm.collector.stream.worker;

/**
 * @author pengys5
 */
public abstract class AbstractLocalWorker extends AbstractWorker {
    public AbstractLocalWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }
}
