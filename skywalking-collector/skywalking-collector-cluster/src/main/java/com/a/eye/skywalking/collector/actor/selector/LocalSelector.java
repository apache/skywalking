package com.a.eye.skywalking.collector.actor.selector;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.WorkerRef;

import java.util.List;

/**
 * The <code>LocalSelector</code> is a simple implementation of {@link WorkerSelector}.
 * It choose {@link WorkerRef} nearly random, by round-robin.
 *
 * @author wusheng
 */
public enum LocalSelector implements WorkerSelector<Object> {
    INSTANCE;

    /**
     * A simple round variable.
     */
    private int index = 0;

    /**
     * Use round-robin to select {@link WorkerRef}.
     *
     * @param members given {@link WorkerRef} list, which size is greater than 0;
     * @param message the {@link AbstractWorker} is going to send.
     * @return the selected {@link WorkerRef}
     */
    @Override
    public WorkerRef select(List<WorkerRef> members, Object message) {
        int size = members.size();
        index++;
        int selectIndex = Math.abs(index) % size;
        return members.get(selectIndex);
    }
}
