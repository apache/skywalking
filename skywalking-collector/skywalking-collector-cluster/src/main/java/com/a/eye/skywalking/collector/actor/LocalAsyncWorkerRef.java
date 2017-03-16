package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public class LocalAsyncWorkerRef extends WorkerRef {

    private AbstractLocalAsyncWorker.WorkerWithDisruptor workerWithDisruptor;

    public LocalAsyncWorkerRef(Role role, AbstractLocalAsyncWorker.WorkerWithDisruptor workerWithDisruptor) {
        super(role);
        this.workerWithDisruptor = workerWithDisruptor;
    }

    @Override
    public void tell(Object message) throws Exception {
        workerWithDisruptor.tell(message);
    }
}
