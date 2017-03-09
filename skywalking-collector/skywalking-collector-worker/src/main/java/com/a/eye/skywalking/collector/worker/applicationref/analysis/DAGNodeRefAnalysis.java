package com.a.eye.skywalking.collector.worker.applicationref.analysis;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.applicationref.receiver.DAGNodeRefReceiver;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.google.gson.JsonObject;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;

/**
 * @author pengys5
 */
public class DAGNodeRefAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(DAGNodeRefAnalysis.class);

    public DAGNodeRefAnalysis(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof RecordPersistenceData) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("frontCode", metric.frontCode);
            propertyJsonObj.addProperty("behindCode", metric.behindCode);

            setRecord(metric.frontCode + "-" + metric.behindCode, propertyJsonObj);
            logger.debug("dag node ref: %s", propertyJsonObj.toString());
        }
    }

    @Override
    protected void aggregation() throws Exception {
        RecordPersistenceData oneRecord;
        while ((oneRecord = pushOneRecord()) != null) {
            tell(DAGNodeRefReceiver.Factory.INSTANCE, HashCodeSelector.INSTANCE, oneRecord);
        }
    }

    public static class Factory extends AbstractAsyncMemberProvider<DAGNodeRefAnalysis> {

        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return DAGNodeRefAnalysis.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.DAGNodeRefAnalysis.Size;
        }
    }

    public static class Metric implements Serializable {
        private final String frontCode;
        private final String behindCode;

        public Metric(String frontCode, String behindCode) {
            this.frontCode = frontCode;
            this.behindCode = behindCode;
        }
    }
}
