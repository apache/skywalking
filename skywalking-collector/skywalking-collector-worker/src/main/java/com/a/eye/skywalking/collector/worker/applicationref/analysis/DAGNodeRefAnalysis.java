package com.a.eye.skywalking.collector.worker.applicationref.analysis;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.applicationref.receiver.DAGNodeRefReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.google.gson.JsonObject;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("frontCode", metric.frontCode);
            propertyJsonObj.addProperty("behindCode", metric.behindCode);
            propertyJsonObj.addProperty(DateTools.Time_Slice_Column_Name, metric.getMinute());

            String id = metric.getMinute() + "-" + metric.frontCode + "-" + metric.behindCode;
            setRecord(id, propertyJsonObj);
            logger.debug("dag node ref: %s", propertyJsonObj.toString());
        }
    }

    @Override
    protected void aggregation() throws Exception {
        RecordData oneRecord;
        while ((oneRecord = pushOne()) != null) {
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

    public static class Metric extends AbstractTimeSlice {
        private final String frontCode;
        private final String behindCode;

        public Metric(long minute, int second, String frontCode, String behindCode) {
            super(minute, second);
            this.frontCode = frontCode;
            this.behindCode = behindCode;
        }
    }
}
