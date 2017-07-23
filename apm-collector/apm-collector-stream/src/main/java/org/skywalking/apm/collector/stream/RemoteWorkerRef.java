package org.skywalking.apm.collector.stream;

/**
 * @author pengys5
 */
public class RemoteWorkerRef extends WorkerRef {

    private AbstractRemoteWorker clusterWorker;

    public RemoteWorkerRef(Role role, AbstractRemoteWorker clusterWorker) {
        super(role);
        this.clusterWorker = clusterWorker;
    }

    @Override
    public void tell(Object message) throws WorkerInvokeException {
        clusterWorker.allocateJob(message);
    }
}
