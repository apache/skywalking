package com.a.eye.skywalking.collector.worker.application;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.application.analysis.DAGNodeAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.NodeInstanceAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.ResponseCostAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.ResponseSummaryAnalysis;
import com.a.eye.skywalking.collector.worker.application.persistence.TraceSegmentRecordPersistence;
import com.a.eye.skywalking.collector.worker.receiver.TraceSegmentReceiver;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.a.eye.skywalking.trace.tag.Tags;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ApplicationMain extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(ApplicationMain.class);

    public ApplicationMain(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFountException {
        getClusterContext().findProvider(DAGNodeAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeInstanceAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(ResponseCostAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(ResponseSummaryAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(TraceSegmentRecordPersistence.Role.INSTANCE).create(this);
    }

    @Override
    public Object onWork(Object message) throws Exception {
        if (message instanceof TraceSegmentReceiver.TraceSegmentTimeSlice) {
            logger.debug("begin translate TraceSegment Object to JsonObject");
            TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment = (TraceSegmentReceiver.TraceSegmentTimeSlice) message;

            getSelfContext().lookup(TraceSegmentRecordPersistence.Role.INSTANCE).tell(traceSegment);

            sendToDAGNodePersistence(traceSegment);
            sendToNodeInstanceAnalysis(traceSegment);
            sendToResponseCostPersistence(traceSegment);
            sendToResponseSummaryPersistence(traceSegment);
        }
        return null;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<ApplicationMain> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return null;
        }

        @Override
        public ApplicationMain workerInstance(ClusterWorkerContext clusterContext) {
            return new ApplicationMain(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ApplicationMain.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }

    private void sendToDAGNodePersistence(TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment) throws Exception {
        String code = traceSegment.getTraceSegment().getApplicationCode();

        String component = null;
        String layer = null;
        for (Span span : traceSegment.getTraceSegment().getSpans()) {
            if (span.getParentSpanId() == -1) {
                component = Tags.COMPONENT.get(span);
                layer = Tags.SPAN_LAYER.get(span);
            }
        }

        DAGNodeAnalysis.Metric node = new DAGNodeAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), code, component, layer);
        getSelfContext().lookup(DAGNodeAnalysis.Role.INSTANCE).tell(node);
    }

    private void sendToNodeInstanceAnalysis(TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment) throws Exception {
        TraceSegmentRef traceSegmentRef = traceSegment.getTraceSegment().getPrimaryRef();

        if (traceSegmentRef != null && !StringUtil.isEmpty(traceSegmentRef.getApplicationCode())) {
            String code = traceSegmentRef.getApplicationCode();
            String address = traceSegmentRef.getPeerHost();

            NodeInstanceAnalysis.Metric property = new NodeInstanceAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), code, address);
            getSelfContext().lookup(NodeInstanceAnalysis.Role.INSTANCE).tell(property);
        }
    }

    private void sendToResponseCostPersistence(TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment) throws Exception {
        String code = traceSegment.getTraceSegment().getApplicationCode();

        long startTime = -1;
        long endTime = -1;
        Boolean isError = false;

        for (Span span : traceSegment.getTraceSegment().getSpans()) {
            if (span.getParentSpanId() == -1) {
                startTime = span.getStartTime();
                endTime = span.getEndTime();
                isError = Tags.ERROR.get(span);
            }
        }

        ResponseCostAnalysis.Metric cost = new ResponseCostAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), code, isError, startTime, endTime);
        getSelfContext().lookup(ResponseCostAnalysis.Role.INSTANCE).tell(cost);
    }

    private void sendToResponseSummaryPersistence(TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment) throws Exception {
        String code = traceSegment.getTraceSegment().getApplicationCode();
        boolean isError = false;

        for (Span span : traceSegment.getTraceSegment().getSpans()) {
            if (span.getParentSpanId() == -1) {
                isError = Tags.ERROR.get(span);
            }
        }

        ResponseSummaryAnalysis.Metric summary = new ResponseSummaryAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), code, isError);
        getSelfContext().lookup(ResponseSummaryAnalysis.Role.INSTANCE).tell(summary);
    }
}
