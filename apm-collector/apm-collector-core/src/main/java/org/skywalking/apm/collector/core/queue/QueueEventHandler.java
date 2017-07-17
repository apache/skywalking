package org.skywalking.apm.collector.core.queue;

/**
 * @author pengys5
 */
public interface QueueEventHandler {
    void tell(Object message);
}
