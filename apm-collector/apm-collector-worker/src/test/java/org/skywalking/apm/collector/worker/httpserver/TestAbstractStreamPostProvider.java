package org.skywalking.apm.collector.worker.httpserver;

import org.skywalking.apm.collector.actor.AbstractWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.Role;

/**
 * @author pengys5
 */
public class TestAbstractStreamPostProvider extends AbstractStreamPostProvider {
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
        return new TestAbstractStreamPost(TestAbstractStreamPost.WorkerRole.INSTANCE, null, null);
    }
}
