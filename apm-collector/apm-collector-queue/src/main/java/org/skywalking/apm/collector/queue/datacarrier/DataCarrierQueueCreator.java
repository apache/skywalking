package org.skywalking.apm.collector.queue.datacarrier;

import org.skywalking.apm.collector.core.queue.QueueCreator;
import org.skywalking.apm.collector.core.queue.QueueEventHandler;
import org.skywalking.apm.collector.core.queue.QueueExecutor;

/**
 * @author pengys5
 */
public class DataCarrierQueueCreator implements QueueCreator {

    @Override public QueueEventHandler create(int queueSize, QueueExecutor executor) {
        return null;
    }
}
