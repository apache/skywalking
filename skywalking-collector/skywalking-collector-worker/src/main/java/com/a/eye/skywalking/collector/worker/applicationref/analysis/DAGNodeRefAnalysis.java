package com.a.eye.skywalking.collector.worker.applicationref.analysis;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.applicationref.receiver.DAGNodeRefReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class DAGNodeRefAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(DAGNodeRefAnalysis.class);

    public DAGNodeRefAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
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
            getClusterContext().lookup(DAGNodeRefReceiver.Role.INSTANCE).tell(oneRecord);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<DAGNodeRefAnalysis> {

        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public DAGNodeRefAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new DAGNodeRefAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.DAGNodeRefAnalysis.Size;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return DAGNodeRefAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
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
