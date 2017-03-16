package com.a.eye.skywalking.collector.worker.application.analysis;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.application.receiver.DAGNodeReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class DAGNodeAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(DAGNodeAnalysis.class);

    public DAGNodeAnalysis(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("code", metric.code);
            propertyJsonObj.addProperty(DateTools.Time_Slice_Column_Name, metric.getMinute());
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
        public Class workerClass() {
            return DAGNodeAnalysis.class;
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    public static class Role extends com.a.eye.skywalking.collector.actor.Role {
        public static Role INSTANCE = new Role();

        @Override
        public String name() {
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

        public Metric(long minute, int second, String code, String component, String layer) {
            super(minute, second);
            this.code = code;
            this.component = component;
            this.layer = layer;
        }
    }
}
