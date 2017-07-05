package org.skywalking.apm.collector.worker.node.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.node.persistence.NodeMappingDayAgg;
import org.skywalking.apm.collector.worker.segment.SegmentReceiver;
import org.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * @author pengys5
 */
public class NodeMappingDayAnalysis extends AbstractNodeMappingAnalysis {

    private Logger logger = LogManager.getFormatterLogger(NodeMappingDayAnalysis.class);

    public NodeMappingDayAnalysis(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof SegmentReceiver.SegmentWithTimeSlice) {
            SegmentReceiver.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentReceiver.SegmentWithTimeSlice)message;
            TraceSegmentObject segment = segmentWithTimeSlice.getSegment();
            analyseRefs(segment, segmentWithTimeSlice.getDay());
        } else {
            logger.error("unhandled message, message instance must SegmentReceiver.SegmentWithTimeSlice, but is %s", message.getClass().toString());
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getClusterContext().lookup(NodeMappingDayAgg.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", NodeMappingDayAgg.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeMappingDayAnalysis> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeMappingDayAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeMappingDayAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Node.NodeMappingDayAnalysis.SIZE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeMappingDayAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
