package org.skywalking.apm.collector.queue.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.skywalking.apm.collector.core.queue.Creator;
import org.skywalking.apm.collector.core.queue.DaemonThreadFactory;
import org.skywalking.apm.collector.core.queue.MessageHolder;
import org.skywalking.apm.collector.core.queue.QueueEventHandler;
import org.skywalking.apm.collector.core.worker.AbstractLocalAsyncWorker;

/**
 * @author pengys5
 */
public class DisruptorCreator implements Creator {

    public QueueEventHandler create(int queueSize, AbstractLocalAsyncWorker localAsyncWorker) {
        // Specify the size of the ring buffer, must be power of 2.
        if (!((((queueSize - 1) & queueSize) == 0) && queueSize != 0)) {
            throw new IllegalArgumentException("queue size must be power of 2");
        }

        // Construct the Disruptor
        Disruptor<MessageHolder> disruptor = new Disruptor(MessageHolderFactory.INSTANCE, queueSize, DaemonThreadFactory.INSTANCE);

        RingBuffer<MessageHolder> ringBuffer = disruptor.getRingBuffer();
        DisruptorEventHandler eventHandler = new DisruptorEventHandler(ringBuffer, localAsyncWorker);

        // Connect the handler
        disruptor.handleEventsWith(eventHandler);

        // Start the Disruptor, starts all threads running
        disruptor.start();
        return eventHandler;
    }
}
