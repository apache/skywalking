package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;

/**
 * The <code>AbstractLocalAsyncWorker</code> should be implemented by any class whose instances
 * are intended to provide receive asynchronous message in same jvm.
 *
 * @author pengys5
 * @since feature3.0
 */
public abstract class AbstractLocalAsyncWorker extends AbstractLocalWorker {

    /**
     * Constructs a <code>AbstractLocalAsyncWorker</code> with the worker role and context.
     *
     * @param role           The responsibility of worker in cluster, more than one workers can have
     *                       same responsibility which use to provide load balancing ability.
     * @param clusterContext See {@link ClusterWorkerContext}
     * @param selfContext    See {@link LocalWorkerContext}
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
     * @throws Exception The Exception happen in {@link #onWork(Object)}
     */
    final public void allocateJob(Object message) throws Exception {
        onWork(message);
    }

    /**
     * The data process logic in this method.
     *
     * @param message Cast the message object to a expect subclass.
     * @throws Exception Don't handle the exception, throw it.
     */
    protected abstract void onWork(Object message) throws Exception;

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
         * @param event      published to the {@link RingBuffer}
         * @param sequence   of the event being processed
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
         * @throws Exception not used.
         */
        public void tell(Object message) throws Exception {
            long sequence = ringBuffer.next();
            try {
                ringBuffer.get(sequence).setMessage(message);
            } finally {
                ringBuffer.publish(sequence);
            }
        }
    }
}
