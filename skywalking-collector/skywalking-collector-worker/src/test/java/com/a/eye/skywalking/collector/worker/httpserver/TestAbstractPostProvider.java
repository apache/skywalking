package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;

/**
 * @author pengys5
 */
public class TestAbstractPostProvider extends AbstractPostProvider {
    @Override
    public int queueSize() {
        return 4;
    }

    @Override
    public String servletPath() {
        return "testPost";
    }

    @Override
    public Role role() {
        return null;
    }

    @Override
    public AbstractWorker workerInstance(ClusterWorkerContext clusterContext) {
        return new TestAbstractPost(TestAbstractPost.WorkerRole.INSTANCE, null, null);
    }
}
