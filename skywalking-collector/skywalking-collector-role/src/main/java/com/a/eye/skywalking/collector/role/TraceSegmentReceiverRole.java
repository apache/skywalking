package com.a.eye.skywalking.collector.role;

import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TraceSegmentReceiverRole extends Role {
    public static TraceSegmentReceiverRole INSTANCE = new TraceSegmentReceiverRole();

    @Override
    public String name() {
        return "TraceSegmentReceiver";
    }

    @Override
    public WorkerSelector workerSelector() {
        return new RollingSelector();
    }
}
