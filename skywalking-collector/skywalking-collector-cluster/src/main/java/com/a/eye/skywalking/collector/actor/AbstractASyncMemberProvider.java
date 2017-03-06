package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.DaemonThreadFactory;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import java.lang.reflect.Constructor;

/**
 * @author pengys5
 */
public abstract class AbstractASyncMemberProvider<T extends EventHandler> {

    private RingBuffer<MessageHolder> ringBuffer;

    public abstract Class memberClass();

    public T createWorker(EventFactory eventFactory, ActorRef actorRef) throws Exception {
        if (memberClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from memberClass()");
        }

        Constructor memberConstructor = memberClass().getDeclaredConstructor(new Class[]{ActorRef.class});
        memberConstructor.setAccessible(true);
        T member = (T) memberConstructor.newInstance(actorRef);

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 1024;
        // Construct the Disruptor
        Disruptor<MessageHolder> disruptor = new Disruptor<MessageHolder>(eventFactory, bufferSize, DaemonThreadFactory.INSTANCE);
        // Connect the handler
        disruptor.handleEventsWith(member);
        // Start the Disruptor, starts all threads running
        disruptor.start();
        // Get the ring buffer from the Disruptor to be used for publishing.
        ringBuffer = disruptor.getRingBuffer();
        return member;
    }

    public void onData(MessageHolder message) {
        long sequence = ringBuffer.next();
        try {
            ringBuffer.get(sequence).setMessage(message);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * Use {@link #memberClass()} method returned class's simple name as a role name.
     *
     * @return is role of Worker
     */
    protected String roleName() {
        return memberClass().getSimpleName();
    }
}
