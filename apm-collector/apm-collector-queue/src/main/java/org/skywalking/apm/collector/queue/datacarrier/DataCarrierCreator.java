package org.skywalking.apm.collector.queue.datacarrier;

import org.skywalking.apm.collector.core.queue.Creator;
import org.skywalking.apm.collector.core.queue.QueueEventHandler;
import org.skywalking.apm.collector.core.worker.AbstractLocalAsyncWorker;

/**
 * @author pengys5
 */
public class DataCarrierCreator implements Creator {

    @Override public QueueEventHandler create(int queueSize, AbstractLocalAsyncWorker localAsyncWorker) {
        return null;
    }
}
