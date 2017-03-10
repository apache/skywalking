package com.a.eye.skywalking.collector.worker.application.persistence;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class DAGNodePersistence extends RecordPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(DAGNodePersistence.class);

    public DAGNodePersistence(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    @Override
    public String esIndex() {
        return "application";
    }

    @Override
    public String esType() {
        return "dag_node";
    }

    public static class Factory extends AbstractAsyncMemberProvider<DAGNodePersistence> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return DAGNodePersistence.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Persistence.DAGNodePersistence.Size;
        }
    }
}
