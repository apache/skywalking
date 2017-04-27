package org.skywalking.apm.collector.commons.role;

import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public enum TraceSegmentReceiverRole implements Role {
    INSTANCE;

    @Override
    public String roleName() {
        return "TraceSegmentReceiver";
    }

    @Override
    public WorkerSelector workerSelector() {
        return new RollingSelector();
    }
}
