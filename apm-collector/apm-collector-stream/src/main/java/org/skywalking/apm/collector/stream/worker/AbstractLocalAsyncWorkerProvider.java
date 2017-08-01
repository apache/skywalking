package org.skywalking.apm.collector.stream.worker;

import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.queue.QueueCreator;
import org.skywalking.apm.collector.core.queue.QueueEventHandler;
import org.skywalking.apm.collector.core.queue.QueueExecutor;
import org.skywalking.apm.collector.queue.QueueModuleContext;
import org.skywalking.apm.collector.queue.QueueModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorkerContainer;

/**
 * @author pengys5
 */
public abstract class AbstractLocalAsyncWorkerProvider<T extends AbstractLocalAsyncWorker & QueueExecutor> extends AbstractLocalWorkerProvider<T> {

    public abstract int queueSize();

    @Override final public WorkerRef create() throws ProviderNotFoundException {
        T localAsyncWorker = workerInstance(getClusterContext());
        localAsyncWorker.preStart();

        if (localAsyncWorker instanceof PersistenceWorker) {
            PersistenceWorkerContainer.INSTANCE.addWorker((PersistenceWorker)localAsyncWorker);
        }

        QueueCreator queueCreator = ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(QueueModuleGroupDefine.GROUP_NAME)).getQueueCreator();
        QueueEventHandler queueEventHandler = queueCreator.create(queueSize(), localAsyncWorker);

        LocalAsyncWorkerRef workerRef = new LocalAsyncWorkerRef(role(), queueEventHandler);
        getClusterContext().put(workerRef);
        return workerRef;
    }
}
