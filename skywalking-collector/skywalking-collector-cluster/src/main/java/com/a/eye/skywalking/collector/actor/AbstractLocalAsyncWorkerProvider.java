package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.queue.DaemonThreadFactory;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.queue.MessageHolderFactory;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import java.lang.reflect.Constructor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalAsyncWorkerProvider<T extends AbstractLocalAsyncWorker> extends AbstractLocalWorkerProvider<T> {

    public abstract int queueSize();

    @Override
    final public WorkerRef create(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws Exception {
        validate();

        Constructor workerConstructor = workerClass().getDeclaredConstructor(new Class<?>[]{Role.class, ClusterWorkerContext.class});
        workerConstructor.setAccessible(true);
        T localAsyncWorker = (T) workerConstructor.newInstance(role(), clusterContext);
        localAsyncWorker.preStart();

        Constructor memberConstructor = AbstractLocalAsyncWorker.WorkerWithDisruptor.class.getDeclaredConstructor(new Class<?>[]{RingBuffer.class, AbstractLocalAsyncWorker.class});
        memberConstructor.setAccessible(true);

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = queueSize();
        // Construct the Disruptor
        Disruptor<MessageHolder> disruptor = new Disruptor<MessageHolder>(MessageHolderFactory.INSTANCE, bufferSize, DaemonThreadFactory.INSTANCE);

        RingBuffer<MessageHolder> ringBuffer = disruptor.getRingBuffer();
        T.WorkerWithDisruptor disruptorWorker = (T.WorkerWithDisruptor) memberConstructor.newInstance(ringBuffer, localAsyncWorker);

        // Connect the handler
        disruptor.handleEventsWith(disruptorWorker);

        // Start the Disruptor, starts all threads running
        disruptor.start();

        LocalAsyncWorkerRef workerRef = new LocalAsyncWorkerRef(role(), disruptorWorker);
        localContext.put(workerRef);
        return workerRef;
    }
}
