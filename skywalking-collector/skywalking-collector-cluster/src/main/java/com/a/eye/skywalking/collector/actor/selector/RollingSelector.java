package com.a.eye.skywalking.collector.actor.selector;

import com.a.eye.skywalking.collector.actor.WorkerRef;

import java.util.List;

/**
 * @author pengys5
 */
public class RollingSelector implements WorkerSelector<WorkerRef> {

    private int index = 0;

    @Override
    public WorkerRef select(List<WorkerRef> members, Object message) {
        int size = members.size();
        index++;
        int selectIndex = Math.abs(index) % size;
        return members.get(selectIndex);
    }
}
