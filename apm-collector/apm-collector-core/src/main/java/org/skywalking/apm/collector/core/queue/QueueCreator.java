package org.skywalking.apm.collector.core.queue;

/**
 * @author pengys5
 */
public interface QueueCreator {
    QueueEventHandler create(int queueSize, QueueExecutor executor);
}
