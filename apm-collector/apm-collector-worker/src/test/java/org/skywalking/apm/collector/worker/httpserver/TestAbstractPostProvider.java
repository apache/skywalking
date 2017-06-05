package org.skywalking.apm.collector.worker.httpserver;

import org.skywalking.apm.collector.actor.AbstractWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerRef;

/**
 * @author pengys5
 */
public class TestAbstractPostProvider extends AbstractPostProvider {

    @Override
    public String servletPath() {
        return "testPost";
    }

    @Override
    public AbstractPostWithHttpServlet handleServlet(WorkerRef workerRef) {
        return new AbstractPost.SegmentPostWithHttpServlet(workerRef);
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
