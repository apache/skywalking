package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.DaemonThreadFactory;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.queue.MessageHolderFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import java.lang.reflect.Constructor;

/**
 * @author pengys5
 */
public abstract class AbstractAsyncMemberProvider<T extends EventHandler> extends AbstractMemberProvider<T> {

    public abstract int queueSize();

    @Override
    public T createWorker(ActorRef actorRef) throws Exception {
        if (memberClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from memberClass()");
        }

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = queueSize();
        // Construct the Disruptor
        Disruptor<MessageHolder> disruptor = new Disruptor<MessageHolder>(MessageHolderFactory.INSTANCE, bufferSize, DaemonThreadFactory.INSTANCE);
        // Start the Disruptor, starts all threads running
        RingBuffer<MessageHolder> ringBuffer = disruptor.start();

        Constructor memberConstructor = memberClass().getDeclaredConstructor(new Class<?>[]{RingBuffer.class, ActorRef.class});
        memberConstructor.setAccessible(true);
        T member = (T) memberConstructor.newInstance(ringBuffer, actorRef);

        // Connect the handler
        disruptor.handleEventsWith(member);
        return member;
    }
}
