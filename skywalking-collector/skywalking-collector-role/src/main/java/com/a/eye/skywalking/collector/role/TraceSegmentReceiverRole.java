package com.a.eye.skywalking.collector.role;

import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

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
