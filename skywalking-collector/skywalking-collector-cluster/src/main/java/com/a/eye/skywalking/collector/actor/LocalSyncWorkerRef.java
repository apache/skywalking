package com.a.eye.skywalking.collector.actor;

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
        localSyncWorker.work(message);
    }
}
