package org.skywalking.apm.collector.core.queue;

import org.skywalking.apm.collector.core.worker.AbstractLocalAsyncWorker;

/**
 * @author pengys5
 */
public interface Creator {
    QueueEventHandler create(int queueSize, AbstractLocalAsyncWorker localAsyncWorker);
}
