package org.skywalking.apm.collector.actor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import org.skywalking.apm.collector.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.queue.MessageHolder;

/**
 * The <code>AbstractLocalAsyncWorker</code> implementations represent workers,
 * which receive local asynchronous message.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractLocalAsyncWorker extends AbstractLocalWorker {

    /**
     * Construct an <code>AbstractLocalAsyncWorker</code> with the worker role and context.
     *
     * @param role The responsibility of worker in cluster, more than one workers can have same responsibility which use
     * to provide load balancing ability.
     * @param clusterContext See {@link ClusterWorkerContext}
     * @param selfContext See {@link LocalWorkerContext}
     */
    public AbstractLocalAsyncWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * The asynchronous worker always use to persistence data into db, this is the end of the streaming,
     * so usually no need to create the next worker instance at the time of this worker instance create.
     *
     * @throws ProviderNotFoundException When worker provider not found, it will be throw this exception.
     */
    @Override
    public void preStart() throws ProviderNotFoundException {
    }

    /**
     * Receive message
     *
     * @param message The persistence data or metric data.
     * @throws WorkerException The Exception happen in {@link #onWork(Object)}
     */
    final public void allocateJob(Object message) throws WorkerException {
        onWork(message);
    }

    /**
     * The data process logic in this method.
     *
     * @param message Cast the message object to a expect subclass.
     * @throws WorkerException Don't handle the exception, throw it.
     */
    protected abstract void onWork(Object message) throws WorkerException;

    static class WorkerWithDisruptor implements EventHandler<MessageHolder> {

        private RingBuffer<MessageHolder> ringBuffer;
        private AbstractLocalAsyncWorker asyncWorker;

        WorkerWithDisruptor(RingBuffer<MessageHolder> ringBuffer, AbstractLocalAsyncWorker asyncWorker) {
            this.ringBuffer = ringBuffer;
            this.asyncWorker = asyncWorker;
        }

        /**
         * Receive the message from disruptor, when message in disruptor is empty, then send the cached data
         * to the next workers.
         *
         * @param event published to the {@link RingBuffer}
         * @param sequence of the event being processed
         * @param endOfBatch flag to indicate if this is the last event in a batch from the {@link RingBuffer}
         */
        public void onEvent(MessageHolder event, long sequence, boolean endOfBatch) {
            try {
                Object message = event.getMessage();
                event.reset();

                asyncWorker.allocateJob(message);
                if (endOfBatch) {
                    asyncWorker.allocateJob(new EndOfBatchCommand());
                }
            } catch (Exception e) {
                asyncWorker.saveException(e);
            }
        }

        /**
         * Push the message into disruptor ring buffer.
         *
         * @param message of the data to process.
         */
        public void tell(Object message) {
            long sequence = ringBuffer.next();
            try {
                ringBuffer.get(sequence).setMessage(message);
            } finally {
                ringBuffer.publish(sequence);
            }
        }
    }
}
