package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

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
    protected void onReceive(String reqJsonStr) throws Exception {

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
        public static Factory INSTANCE = new Factory();

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
