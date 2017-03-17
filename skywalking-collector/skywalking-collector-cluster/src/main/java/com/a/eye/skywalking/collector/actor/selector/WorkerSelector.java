package com.a.eye.skywalking.collector.actor.selector;

import com.a.eye.skywalking.collector.actor.WorkerRef;

import java.util.List;

/**
 * @author pengys5
 */
public interface WorkerSelector<T extends WorkerRef> {
    T select(List<T> members, Object message);
}
