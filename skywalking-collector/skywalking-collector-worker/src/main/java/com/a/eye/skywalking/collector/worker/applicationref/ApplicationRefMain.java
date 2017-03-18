package com.a.eye.skywalking.collector.worker.applicationref;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.applicationref.analysis.DAGNodeRefAnalysis;
import com.a.eye.skywalking.collector.worker.receiver.TraceSegmentReceiver;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import java.util.List;

/**
 * @author pengys5
 */
public class ApplicationRefMain extends AbstractLocalSyncWorker {

    private DAGNodeRefAnalysis dagNodeRefAnalysis;

    public ApplicationRefMain(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(DAGNodeRefAnalysis.Role.INSTANCE).create(this);
    }

    @Override
    public void work(Object message) throws Exception {
        TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment = (TraceSegmentReceiver.TraceSegmentTimeSlice) message;

        TraceSegment segment = traceSegment.getTraceSegment();
        List<TraceSegmentRef> refs = segment.getRefs();
        if(refs != null){
            for (TraceSegmentRef ref : refs) {
                String front = ref.getApplicationCode();
                String behind = segment.getApplicationCode();

                DAGNodeRefAnalysis.Metric nodeRef = new DAGNodeRefAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), front, behind);
                getSelfContext().lookup(DAGNodeRefAnalysis.Role.INSTANCE).tell(nodeRef);
            }
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<ApplicationRefMain> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public ApplicationRefMain workerInstance(ClusterWorkerContext clusterContext) {
            return new ApplicationRefMain(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ApplicationRefMain.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
