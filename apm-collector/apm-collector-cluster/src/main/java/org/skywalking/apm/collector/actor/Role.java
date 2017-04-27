package org.skywalking.apm.collector.actor;

import org.skywalking.apm.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public interface Role {

    String roleName();

    WorkerSelector workerSelector();
}
