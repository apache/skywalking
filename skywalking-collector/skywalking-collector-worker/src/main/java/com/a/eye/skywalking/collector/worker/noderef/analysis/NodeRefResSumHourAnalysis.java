package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefResSumHourAgg;
import com.a.eye.skywalking.collector.worker.storage.MetricData;

/**
 * @author pengys5
 */
public class NodeRefResSumHourAnalysis extends AbstractNodeRefResSumAnalysis {

    private NodeRefResSumHourAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof NodeRefResRecord) {
            NodeRefResRecord refResRecord = (NodeRefResRecord) message;
            analyseResSum(refResRecord);
        }
    }

    @Override
    protected void aggregation() throws Exception {
        MetricData oneMetric;
        while ((oneMetric = pushOne()) != null) {
            getClusterContext().lookup(NodeRefResSumHourAgg.Role.INSTANCE).tell(oneMetric);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeRefResSumHourAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefResSumHourAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefResSumHourAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.ResponseSummaryAnalysis.Size;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumHourAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
