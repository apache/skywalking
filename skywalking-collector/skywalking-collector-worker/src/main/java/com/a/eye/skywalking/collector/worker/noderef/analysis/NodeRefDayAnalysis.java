package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefDayAgg;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class NodeRefDayAnalysis extends AbstractNodeRefAnalysis {

    private Logger logger = LogManager.getFormatterLogger(NodeRefDayAnalysis.class);

    protected NodeRefDayAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
                                 LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        super.preStart();
        getClusterContext().findProvider(NodeRefResSumDayAnalysis.Role.INSTANCE).create(this);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice) message;
            Segment segment = segmentWithTimeSlice.getSegment();

            long minute = segmentWithTimeSlice.getMinute();
            long hour = segmentWithTimeSlice.getHour();
            long day = segmentWithTimeSlice.getDay();
            int second = segmentWithTimeSlice.getSecond();
            analyseNodeRef(segment, segmentWithTimeSlice.getDay(), minute, hour, day, second);
        } else {
            logger.error("unhandled message, message instance must SegmentPost.SegmentWithTimeSlice, but is %s", message.getClass().toString());
        }
    }

    @Override
    protected void sendToResSumAnalysis(AbstractNodeRefResSumAnalysis.NodeRefResRecord refResRecord) throws Exception {
        getSelfContext().lookup(NodeRefResSumDayAnalysis.Role.INSTANCE).tell(refResRecord);
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getClusterContext().lookup(NodeRefDayAgg.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", NodeRefDayAgg.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeRefDayAnalysis> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefDayAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefDayAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.NodeRef.NodeRefDayAnalysis.SIZE;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefDayAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
