package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;

/**
 * @author pengys5
 */
public abstract class AbstractLocalAsyncWorker extends AbstractLocalWorker {

    public AbstractLocalAsyncWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    static class WorkerWithDisruptor implements EventHandler<MessageHolder> {

        private RingBuffer<MessageHolder> ringBuffer;
        private AbstractLocalAsyncWorker asyncWorker;

        public WorkerWithDisruptor(RingBuffer<MessageHolder> ringBuffer, AbstractLocalAsyncWorker asyncWorker) {
            this.ringBuffer = ringBuffer;
            this.asyncWorker = asyncWorker;
        }

        public void onEvent(MessageHolder event, long sequence, boolean endOfBatch) {
            try {
                Object message = event.getMessage();
                event.reset();
                asyncWorker.work(message);
                if (endOfBatch) {
                    asyncWorker.work(new EndOfBatchCommand());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

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
