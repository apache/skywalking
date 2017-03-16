package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public abstract class Role {

    public abstract String name();

    public abstract WorkerSelector workerSelector();
}
