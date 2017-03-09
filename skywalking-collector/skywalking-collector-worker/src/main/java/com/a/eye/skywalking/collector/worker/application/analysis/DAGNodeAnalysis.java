package com.a.eye.skywalking.collector.worker.application.analysis;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.application.receiver.DAGNodeReceiver;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.google.gson.JsonObject;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;

/**
 * @author pengys5
 */
public class DAGNodeAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(DAGNodeAnalysis.class);

    public DAGNodeAnalysis(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("code", metric.code);
            propertyJsonObj.addProperty("component", metric.component);
            propertyJsonObj.addProperty("layer", metric.layer);

            logger.debug("dag node: %s", propertyJsonObj.toString());
            setRecord(metric.code, propertyJsonObj);
        } else {
            logger.error("message unhandled");
        }
    }

    @Override
    protected void aggregation() throws Exception {
        RecordPersistenceData oneRecord;
        while ((oneRecord = pushOneRecord()) != null) {
            tell(DAGNodeReceiver.Factory.INSTANCE, HashCodeSelector.INSTANCE, oneRecord);
        }
    }

    public static class Factory extends AbstractAsyncMemberProvider<DAGNodeAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return DAGNodeAnalysis.class;
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    public static class Metric implements Serializable {
        private final String code;
        private final String component;
        private final String layer;

        public Metric(String code, String component, String layer) {
            this.code = code;
            this.component = component;
            this.layer = layer;
        }
    }
}
