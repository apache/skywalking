package org.skywalking.apm.collector.worker.httpserver;

import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestAbstractPost extends AbstractPost {
    public TestAbstractPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override
    protected void onReceive(Object message) throws Exception {
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return TestAbstractPost.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }

    public static class Factory extends AbstractPostProvider<TestAbstractPost> {
        @Override
        public String servletPath() {
            return "/TestAbstractPost";
        }

        @Override
        public int queueSize() {
            return 4;
        }

        @Override
        public Role role() {
            return TestAbstractPost.WorkerRole.INSTANCE;
        }

        @Override
        public TestAbstractPost workerInstance(ClusterWorkerContext clusterContext) {
            return new TestAbstractPost(role(), clusterContext, new LocalWorkerContext());
        }
    }
}
