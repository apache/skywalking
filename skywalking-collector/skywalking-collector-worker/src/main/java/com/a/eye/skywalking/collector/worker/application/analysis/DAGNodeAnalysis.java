package com.a.eye.skywalking.collector.worker.application.analysis;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.application.receiver.DAGNodeReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.storage.index.AbstractIndex;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class DAGNodeAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(DAGNodeAnalysis.class);

    public DAGNodeAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("code", metric.code);
            propertyJsonObj.addProperty(AbstractIndex.Time_Slice_Column_Name, metric.getMinute());
            propertyJsonObj.addProperty("component", metric.component);
            propertyJsonObj.addProperty("layer", metric.layer);

            String id = metric.getMinute() + "-" + metric.code;
            logger.debug("dag node: %s", propertyJsonObj.toString());
            setRecord(id, propertyJsonObj);
        } else {
            logger.error("message unhandled");
        }
    }

    @Override
    protected void aggregation() throws Exception {
        RecordData oneRecord;
        while ((oneRecord = pushOne()) != null) {
            getClusterContext().lookup(DAGNodeReceiver.Role.INSTANCE).tell(oneRecord);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<DAGNodeAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public DAGNodeAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new DAGNodeAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return DAGNodeAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }

    public static class Metric extends AbstractTimeSlice {
        private final String code;
        private final String component;
        private final String layer;

        public Metric(long minute, long hour, long day, int second, String code, String component, String layer) {
            super(minute, hour, day, second);
            this.code = code;
            this.component = component;
            this.layer = layer;
        }
    }
}
