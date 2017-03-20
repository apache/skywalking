package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestLocalAsyncWorker extends AbstractLocalAsyncWorker {

    public TestLocalAsyncWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {

    }

    @Override
    public void work(Object message) throws Exception {
        if (message.equals("TellLocalAsyncWorker")) {
            System.out.println("hello async!");
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<TestLocalAsyncWorker> {

        @Override
        public int queueSize() {
            return 1024;
        }

        @Override
        public Role role() {
            return TestLocalASyncWorkerRole.INSTANCE;
        }

        @Override
        public TestLocalAsyncWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new TestLocalAsyncWorker(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum TestLocalASyncWorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return TestLocalAsyncWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
