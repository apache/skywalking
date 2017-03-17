package com.a.eye.skywalking.collector.actor.selector;

import com.a.eye.skywalking.collector.actor.WorkerRef;

import java.util.List;

/**
 * @author pengys5
 */
public class HashCodeSelector implements WorkerSelector<WorkerRef> {

    @Override
    public WorkerRef select(List<WorkerRef> members, Object message) {
        if (message instanceof AbstractHashMessage) {
            AbstractHashMessage hashMessage = (AbstractHashMessage) message;
            int size = members.size();
            int selectIndex = Math.abs(hashMessage.getHashCode()) % size;
            return members.get(selectIndex);
        } else {
            throw new IllegalArgumentException("the message send into HashCodeSelector must implementation of AbstractHashMessage");
        }
    }
}
