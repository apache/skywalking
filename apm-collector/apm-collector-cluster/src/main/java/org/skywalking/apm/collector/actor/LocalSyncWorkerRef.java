package org.skywalking.apm.collector.actor;

/**
 * @author pengys5
 */
public class LocalSyncWorkerRef extends WorkerRef {

    private AbstractLocalSyncWorker localSyncWorker;

    public LocalSyncWorkerRef(Role role, AbstractLocalSyncWorker localSyncWorker) {
        super(role);
        this.localSyncWorker = localSyncWorker;
    }

    @Override
    public void tell(Object message) throws Exception {
        localSyncWorker.allocateJob(message, null);
    }

    public void ask(Object request, Object response) throws Exception {
        localSyncWorker.allocateJob(request, response);
    }
}
