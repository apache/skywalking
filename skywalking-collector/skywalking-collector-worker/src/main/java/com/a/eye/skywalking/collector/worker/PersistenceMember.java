package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMember;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public abstract class PersistenceMember extends AbstractAsyncMember {

    private Logger logger = LogManager.getFormatterLogger(PersistenceMember.class);

    public PersistenceMember(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    public abstract String esIndex();

    public abstract String esType();

    public abstract void analyse(Object message) throws Exception;

    @Override
    public void receive(Object message) throws Exception {
        if (message instanceof EndOfBatchCommand) {
            persistence();
        } else {
            analyse(message);
        }
    }

    protected abstract void persistence();
}
