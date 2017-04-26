package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;

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
