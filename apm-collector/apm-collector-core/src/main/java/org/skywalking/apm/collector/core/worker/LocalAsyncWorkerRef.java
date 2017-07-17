package org.skywalking.apm.collector.core.worker;

import org.skywalking.apm.collector.core.queue.QueueEventHandler;

/**
 * @author pengys5
 */
public class LocalAsyncWorkerRef extends WorkerRef {

    private QueueEventHandler queueEventHandler;

    public LocalAsyncWorkerRef(Role role, QueueEventHandler queueEventHandler) {
        super(role);
        this.queueEventHandler = queueEventHandler;
    }

    @Override
    public void tell(Object message) throws WorkerInvokeException {
        queueEventHandler.tell(message);
    }
}
