package org.skywalking.apm.collector.agentstream.worker.node.component;

import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;

/**
 * @author pengys5
 */
public class NodeComponentRemoteWorker extends AbstractRemoteWorker {

    protected NodeComponentRemoteWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {

    }

    @Override protected void onWork(Object message) throws WorkerException {

    }
}
