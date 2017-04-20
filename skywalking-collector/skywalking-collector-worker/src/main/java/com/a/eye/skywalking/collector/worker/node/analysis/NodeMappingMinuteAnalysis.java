package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeMappingMinuteAgg;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * @author pengys5
 */
public class NodeMappingMinuteAnalysis extends AbstractNodeMappingAnalysis {

    NodeMappingMinuteAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice)message;
            TraceSegment segment = segmentWithTimeSlice.getTraceSegment();
            analyseRefs(segment, segmentWithTimeSlice.getMinute());
        }
    }

    @Override
    protected void aggregation() throws Exception {
        RecordData oneRecord;
        while ((oneRecord = pushOne()) != null) {
            getClusterContext().lookup(NodeMappingMinuteAgg.Role.INSTANCE).tell(oneRecord);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeMappingMinuteAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeMappingMinuteAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeMappingMinuteAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Node.NodeMappingMinuteAnalysis.SIZE;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeMappingMinuteAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
