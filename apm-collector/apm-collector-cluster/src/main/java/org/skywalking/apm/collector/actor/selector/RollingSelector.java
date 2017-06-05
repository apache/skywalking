package org.skywalking.apm.collector.actor.selector;

import org.skywalking.apm.collector.actor.AbstractWorker;
import org.skywalking.apm.collector.actor.WorkerRef;

import java.util.List;

/**
 * The <code>RollingSelector</code> is a simple implementation of {@link WorkerSelector}.
 * It choose {@link WorkerRef} nearly random, by round-robin.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public class RollingSelector implements WorkerSelector<WorkerRef> {

    private int index = 0;

    /**
     * Use round-robin to select {@link WorkerRef}.
     *
     * @param members given {@link WorkerRef} list, which size is greater than 0;
     * @param message message the {@link AbstractWorker} is going to send.
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
