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
import org.skywalking.apm.collector.worker.node.persistence.NodeCompAgg;
import org.skywalking.apm.collector.worker.segment.SegmentPost;
import org.skywalking.apm.collector.worker.segment.entity.Segment;

/**
 * @author pengys5
 */
public class NodeCompAnalysis extends AbstractNodeCompAnalysis {

    private Logger logger = LogManager.getFormatterLogger(NodeCompAnalysis.class);

    NodeCompAnalysis(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice)message;
            Segment segment = segmentWithTimeSlice.getSegment();
            analyseSpans(segment);
        } else {
            logger.error("unhandled message, message instance must SegmentPost.SegmentWithTimeSlice, but is %s", message.getClass().toString());
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getClusterContext().lookup(NodeCompAgg.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", NodeCompAgg.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeCompAnalysis> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeCompAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeCompAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Node.NodeCompAnalysis.SIZE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeCompAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
