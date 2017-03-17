package com.a.eye.skywalking.collector.worker.application.analysis;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.receiver.NodeInstanceReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class NodeInstanceAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(NodeInstanceAnalysis.class);

    public NodeInstanceAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("code", metric.code);
            propertyJsonObj.addProperty(DateTools.Time_Slice_Column_Name, metric.getMinute());
            propertyJsonObj.addProperty("address", metric.address);

            String id = metric.getMinute() + "-" + metric.address;
            setRecord(id, propertyJsonObj);
            logger.debug("node instance: %s", propertyJsonObj.toString());
        } else {
            logger.error("message unhandled");
        }
    }

    @Override
    protected void aggregation() throws Exception {
        RecordData oneRecord;
        while ((oneRecord = pushOne()) != null) {
            getClusterContext().lookup(NodeInstanceReceiver.Role.INSTANCE).tell(oneRecord);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeInstanceAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return null;
        }

        @Override
        public NodeInstanceAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeInstanceAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.NodeInstanceAnalysis.Size;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeInstanceAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }

    public static class Metric extends AbstractTimeSlice {
        private final String code;
        private final String address;

        public Metric(long minute, int second, String code, String address) {
            super(minute, second);
            this.code = code;
            this.address = address;
        }
    }
}
