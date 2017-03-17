package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.queue.DaemonThreadFactory;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.queue.MessageHolderFactory;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalAsyncWorkerProvider<T extends AbstractLocalAsyncWorker> extends AbstractLocalWorkerProvider<T> {

    public abstract int queueSize();

    @Override
    final public WorkerRef onCreate(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFountException {
        T localAsyncWorker = (T) workerInstance(clusterContext);
        localAsyncWorker.preStart();

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = queueSize();
        if (!((((bufferSize - 1) & bufferSize) == 0) && bufferSize != 0)) {
            throw new IllegalArgumentException("queue size must be power of 2");
        }

        // Construct the Disruptor
        Disruptor<MessageHolder> disruptor = new Disruptor<MessageHolder>(MessageHolderFactory.INSTANCE, bufferSize, DaemonThreadFactory.INSTANCE);

        RingBuffer<MessageHolder> ringBuffer = disruptor.getRingBuffer();
        T.WorkerWithDisruptor disruptorWorker = new T.WorkerWithDisruptor(ringBuffer, localAsyncWorker);

        // Connect the handler
        disruptor.handleEventsWith(disruptorWorker);

        // Start the Disruptor, starts all threads running
        disruptor.start();

        LocalAsyncWorkerRef workerRef = new LocalAsyncWorkerRef(role(), disruptorWorker);
        localContext.put(workerRef);
        return workerRef;
    }
}
