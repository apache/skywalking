package org.skywalking.apm.collector.worker.noderef.analysis;

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
import org.skywalking.apm.collector.worker.noderef.persistence.NodeRefResSumMinuteAgg;

/**
 * @author pengys5
 */
public class NodeRefResSumMinuteAnalysis extends AbstractNodeRefResSumAnalysis {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumMinuteAnalysis.class);

    NodeRefResSumMinuteAnalysis(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof NodeRefResRecord) {
            NodeRefResRecord refResRecord = (NodeRefResRecord)message;
            analyseResSum(refResRecord);
        } else {
            logger.error("unhandled message, message instance must NodeRefResRecord, but is %s", message.getClass().toString());
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getClusterContext().lookup(NodeRefResSumMinuteAgg.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", NodeRefResSumMinuteAgg.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeRefResSumMinuteAnalysis> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefResSumMinuteAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefResSumMinuteAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.NodeRef.NodeRefResSumMinuteAnalysis.SIZE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumMinuteAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
