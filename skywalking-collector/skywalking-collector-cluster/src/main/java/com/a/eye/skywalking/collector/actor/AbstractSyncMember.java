package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.MessageHolder;

/**
 * @author pengys5
 */
public abstract class AbstractSyncMember extends AbstractMember {

    public AbstractSyncMember(ActorRef actorRef) {
        super(actorRef);
    }

    @Override
    public void onEvent(MessageHolder event, long sequence, boolean endOfBatch) throws Exception {
    }

    @Override
    public void beTold(Object message) throws Exception {
        receive(message);
    }
}
