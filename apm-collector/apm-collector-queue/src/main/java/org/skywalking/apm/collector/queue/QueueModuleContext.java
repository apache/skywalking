package org.skywalking.apm.collector.queue;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.queue.QueueCreator;

/**
 * @author pengys5
 */
public class QueueModuleContext extends Context {
    private QueueCreator queueCreator;

    public QueueModuleContext(String groupName) {
        super(groupName);
    }

    public QueueCreator getQueueCreator() {
        return queueCreator;
    }

    public void setQueueCreator(QueueCreator queueCreator) {
        this.queueCreator = queueCreator;
    }
}
