package org.skywalking.apm.collector.worker.segment.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordAnalysisMember;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.segment.entity.SegmentAndJson;
import org.skywalking.apm.collector.worker.segment.persistence.SegmentSave;

/**
 * @author pengys5
 */
public class SegmentAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(SegmentAnalysis.class);

    SegmentAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentSave.Role.INSTANCE).create(this);
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof SegmentAndJson) {
            SegmentAndJson segmentAndJson = (SegmentAndJson)message;

            try {
                getSelfContext().lookup(SegmentSave.Role.INSTANCE).tell(segmentAndJson);
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                e.printStackTrace();
                logger.error(e.getMessage(), e);
            }
        } else {
            logger.error("unhandled message, message instance must Segment, but is %s", message.getClass().toString());
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentAnalysis> {
        @Override
        public Role role() {
            return SegmentAnalysis.Role.INSTANCE;
        }

        @Override
        public SegmentAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentAnalysis.SIZE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
