package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.lmax.disruptor.RingBuffer;

/**
 * @author pengys5
 */
public abstract class AbstractAsyncMember extends AbstractMember {

    private RingBuffer<MessageHolder> ringBuffer;

    public AbstractAsyncMember(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(actorRef);
        this.ringBuffer = ringBuffer;
    }

    public void onEvent(MessageHolder event, long sequence, boolean endOfBatch) throws Exception {
        Object message = event.getMessage();
        event.reset();
        receive(message);
        if (endOfBatch) {
            receive(new EndOfBatchCommand());
        }
    }

    public void beTold(Object message) throws Exception {
        long sequence = ringBuffer.next();
        try {
            ringBuffer.get(sequence).setMessage(message);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
