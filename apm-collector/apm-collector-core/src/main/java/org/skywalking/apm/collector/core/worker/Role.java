package org.skywalking.apm.collector.core.worker;

import org.skywalking.apm.collector.core.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public interface Role {

    String roleName();

    WorkerSelector workerSelector();
}
