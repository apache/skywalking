package org.skywalking.apm.collector.stream.worker;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class ClusterWorkerContext extends WorkerContext {

    private List<AbstractRemoteWorkerProvider> providers = new ArrayList<>();

    public List<AbstractRemoteWorkerProvider> getProviders() {
        return providers;
    }

    @Override
    public void putProvider(AbstractRemoteWorkerProvider provider) {
        providers.add(provider);
    }
}
