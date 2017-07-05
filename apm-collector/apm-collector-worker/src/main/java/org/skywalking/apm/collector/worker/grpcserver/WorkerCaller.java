package org.skywalking.apm.collector.worker.grpcserver;

import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;

/**
 * @author pengys5
 */
public interface WorkerCaller {
    void preStart() throws ProviderNotFoundException;

    void inject(ClusterWorkerContext clusterWorkerContext);
}
