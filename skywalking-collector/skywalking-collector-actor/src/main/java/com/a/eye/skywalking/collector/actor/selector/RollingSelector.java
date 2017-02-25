package com.a.eye.skywalking.collector.actor.selector;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractWorker;
import java.util.List;

/**
 * The <code>RollingSelector</code> is a simple implementation of {@link WorkerSelector}.
 * It choose {@link ActorRef} nearly random, by round-robin.
 *
 * @author wusheng
 */
public enum RollingSelector implements WorkerSelector<Object> {
    INSTANCE;

    /**
     * A simple round variable.
     */
    private int index = 0;

    /**
     * Use round-robin to select {@link ActorRef}.
     *
     * @param members given {@link ActorRef} list, which size is greater than 0;
     * @param message the {@link AbstractWorker} is going to send.
     * @return the selected {@link ActorRef}
     */
    @Override
    public ActorRef select(List<ActorRef> members, Object message) {
        int size = members.size();
        index++;
        int selectIndex = Math.abs(index) % size;
        return members.get(selectIndex);
    }
}
