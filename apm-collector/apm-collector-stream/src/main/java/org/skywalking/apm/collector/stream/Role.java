package org.skywalking.apm.collector.stream;

import org.skywalking.apm.collector.stream.selector.WorkerSelector;

/**
 * @author pengys5
 */
public interface Role {

    String roleName();

    WorkerSelector workerSelector();
}
