package org.skywalking.apm.collector.stream.impl;

import org.skywalking.apm.collector.stream.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.LocalWorkerContext;
import org.skywalking.apm.collector.stream.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.Role;
import org.skywalking.apm.collector.stream.WorkerException;

/**
 * @author pengys5
 */
public class GRPCRemoteWorker extends AbstractRemoteWorker {

    protected GRPCRemoteWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        
    }

    @Override protected final void onWork(Object message) throws WorkerException {

    }
}
