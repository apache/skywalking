package com.a.eye.skywalking.collector.worker.applicationref.persistence;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class DAGNodeRefPersistence extends RecordPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(DAGNodeRefPersistence.class);

    public DAGNodeRefPersistence(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    @Override
    public String esIndex() {
        return "node_ref";
    }

    @Override
    public String esType() {
        return "node_ref";
    }

    public static class Factory extends AbstractAsyncMemberProvider<DAGNodeRefPersistence> {

        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return DAGNodeRefPersistence.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Persistence.DAGNodeRefPersistence.Size;
        }
    }
}
