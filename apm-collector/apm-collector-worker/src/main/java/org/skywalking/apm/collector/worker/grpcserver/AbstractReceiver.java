package org.skywalking.apm.collector.worker.grpcserver;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerException;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;

/**
 * @author pengys5
 */
public abstract class AbstractReceiver extends AbstractLocalSyncWorker {

    public AbstractReceiver(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final @Override protected void onWork(Object request, Object response) throws WorkerException {
        onReceive(request);
    }

    protected abstract void onReceive(
        Object request) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException;
}
