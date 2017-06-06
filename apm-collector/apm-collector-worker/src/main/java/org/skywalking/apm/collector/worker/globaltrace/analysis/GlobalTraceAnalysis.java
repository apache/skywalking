package org.skywalking.apm.collector.worker.globaltrace.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.*;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.JoinAndSplitAnalysisMember;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.globaltrace.GlobalTraceIndex;
import org.skywalking.apm.collector.worker.globaltrace.persistence.GlobalTraceAgg;
import org.skywalking.apm.collector.worker.segment.SegmentPost;
import org.skywalking.apm.collector.worker.segment.entity.GlobalTraceId;
import org.skywalking.apm.collector.worker.segment.entity.Segment;
import org.skywalking.apm.collector.worker.tools.CollectionTools;

import java.util.List;

/**
 * @author pengys5
 */
public class GlobalTraceAnalysis extends JoinAndSplitAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(GlobalTraceAnalysis.class);

    GlobalTraceAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice) message;
            Segment segment = segmentWithTimeSlice.getSegment();
            String subSegmentId = segment.getTraceSegmentId();
            List<GlobalTraceId> globalTraceIdList = null;
//            List<GlobalTraceId> globalTraceIdList = segment.getRelatedGlobalTraces();
            if (CollectionTools.isNotEmpty(globalTraceIdList)) {
                for (GlobalTraceId disTraceId : globalTraceIdList) {
//                    String traceId = disTraceId.get();
//                    set(traceId, GlobalTraceIndex.SUB_SEG_IDS, subSegmentId);
                }
            }
        } else {
            logger.error("unhandled message, message instance must SegmentPost.SegmentWithTimeSlice, but is %s", message.getClass().toString());
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getClusterContext().lookup(GlobalTraceAgg.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", GlobalTraceAgg.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<GlobalTraceAnalysis> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public GlobalTraceAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new GlobalTraceAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.GlobalTrace.GlobalTraceAnalysis.SIZE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return GlobalTraceAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
