package org.skywalking.apm.collector.core.worker;

import org.skywalking.apm.collector.core.queue.QueueEventHandler;
import org.skywalking.apm.collector.core.queue.QueueModuleContext;

/**
 * @author pengys5
 */
public abstract class AbstractLocalAsyncWorkerProvider<T extends AbstractLocalAsyncWorker> extends AbstractLocalWorkerProvider<T> {

    public abstract int queueSize();

    @Override final public WorkerRef onCreate(
        LocalWorkerContext localContext) throws ProviderNotFoundException {
        T localAsyncWorker = workerInstance(getClusterContext());
        localAsyncWorker.preStart();

        QueueEventHandler queueEventHandler = QueueModuleContext.creator.create(queueSize(), localAsyncWorker);

        LocalAsyncWorkerRef workerRef = new LocalAsyncWorkerRef(role(), queueEventHandler);

        if (localContext != null) {
            localContext.put(workerRef);
        }

        return workerRef;
    }
}
