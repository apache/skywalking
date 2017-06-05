package org.skywalking.apm.collector.worker.httpserver;

import org.skywalking.apm.collector.actor.AbstractWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.Role;

/**
 * @author pengys5
 */
public class TestAbstractGetProvider extends AbstractGetProvider {
    @Override
    public String servletPath() {
        return "servletPath";
    }

    @Override
    public Role role() {
        return TestAbstractGet.WorkerRole.INSTANCE;
    }

    @Override
    public AbstractWorker workerInstance(ClusterWorkerContext clusterContext) {
        return new TestAbstractGet(role(), null, null);
    }
}
