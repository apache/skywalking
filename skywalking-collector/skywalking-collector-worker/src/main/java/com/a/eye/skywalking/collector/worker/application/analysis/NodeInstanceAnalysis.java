package com.a.eye.skywalking.collector.worker.application.analysis;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.receiver.DAGNodeReceiver;
import com.a.eye.skywalking.collector.worker.application.receiver.NodeInstanceReceiver;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.google.gson.JsonObject;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class NodeInstanceAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(NodeInstanceAnalysis.class);

    public NodeInstanceAnalysis(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("code", metric.code);
            propertyJsonObj.addProperty("address", metric.address);

            setRecord(metric.address, propertyJsonObj);
            logger.debug("node instance: %s", propertyJsonObj.toString());
        } else {
            logger.error("message unhandled");
        }
    }

    @Override
    protected void aggregation() throws Exception {
        RecordPersistenceData oneRecord;
        while ((oneRecord = pushOneRecord()) != null) {
            tell(NodeInstanceReceiver.Factory.INSTANCE, HashCodeSelector.INSTANCE, oneRecord);
        }
    }

    public static class Factory extends AbstractAsyncMemberProvider<NodeInstanceAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return NodeInstanceAnalysis.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.NodeInstanceAnalysis.Size;
        }
    }

    public static class Metric {
        private final String code;
        private final String address;

        public Metric(String code, String address) {
            this.code = code;
            this.address = address;
        }
    }
}
