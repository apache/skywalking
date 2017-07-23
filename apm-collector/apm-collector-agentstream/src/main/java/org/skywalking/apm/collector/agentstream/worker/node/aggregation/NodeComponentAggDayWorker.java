package org.skywalking.apm.collector.agentstream.worker.node.aggregation;

import org.skywalking.apm.collector.stream.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.LocalWorkerContext;
import org.skywalking.apm.collector.stream.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.Role;
import org.skywalking.apm.collector.stream.impl.AggregationWorker;

/**
 * @author pengys5
 */
public class NodeComponentAggDayWorker extends AggregationWorker {

    public NodeComponentAggDayWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected void sendToNext() {
        
    }
}
