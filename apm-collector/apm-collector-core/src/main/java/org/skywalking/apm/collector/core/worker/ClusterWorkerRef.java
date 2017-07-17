package org.skywalking.apm.collector.core.worker;

/**
 * @author pengys5
 */
public class ClusterWorkerRef extends WorkerRef {

    private AbstractClusterWorker clusterWorker;

    public ClusterWorkerRef(Role role, AbstractClusterWorker clusterWorker) {
        super(role);
        this.clusterWorker = clusterWorker;
    }

    @Override
    public void tell(Object message) throws WorkerInvokeException {
        clusterWorker.allocateJob(message);
    }
}
