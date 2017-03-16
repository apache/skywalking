package com.a.eye.skywalking.collector.worker.applicationref;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorker;
import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.applicationref.analysis.DAGNodeRefAnalysis;
import com.a.eye.skywalking.collector.worker.receiver.TraceSegmentReceiver;
import com.a.eye.skywalking.trace.TraceSegmentRef;

/**
 * @author pengys5
 */
public class ApplicationRefMain extends AbstractLocalSyncWorker {

    private DAGNodeRefAnalysis dagNodeRefAnalysis;

    public ApplicationRefMain(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public void preStart() throws Exception {
        getClusterContext().findProvider(DAGNodeRefAnalysis.Role.INSTANCE).create(getClusterContext(), getSelfContext());
    }

    @Override
    public void work(Object message) throws Exception {
        TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment = (TraceSegmentReceiver.TraceSegmentTimeSlice) message;

        TraceSegmentRef traceSegmentRef = traceSegment.getTraceSegment().getPrimaryRef();
        if (traceSegmentRef != null && !StringUtil.isEmpty(traceSegmentRef.getApplicationCode())) {
            String front = traceSegmentRef.getApplicationCode();
            String behind = traceSegment.getTraceSegment().getApplicationCode();

            DAGNodeRefAnalysis.Metric nodeRef = new DAGNodeRefAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), front, behind);
            getSelfContext().lookup(DAGNodeRefAnalysis.Role.INSTANCE).tell(nodeRef);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<ApplicationRefMain> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public Class workerClass() {
            return ApplicationRefMain.class;
        }
    }

    public static class Role extends com.a.eye.skywalking.collector.actor.Role {
        public static Role INSTANCE = new Role();

        @Override
        public String name() {
            return ApplicationRefMain.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
