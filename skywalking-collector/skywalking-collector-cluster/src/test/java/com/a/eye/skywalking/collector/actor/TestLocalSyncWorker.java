package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestLocalSyncWorker extends AbstractLocalSyncWorker {

    public TestLocalSyncWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFountException {

    }

    @Override
    public void work(Object message) throws Exception {
        if (message.equals("TellLocalWorker")) {
            System.out.println("hello! ");
        } else {
            System.out.println("unhandled");
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<TestLocalSyncWorker> {
        @Override
        public Role role() {
            return TestLocalSyncWorkerRole.INSTANCE;
        }

        @Override
        public TestLocalSyncWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new TestLocalSyncWorker(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum TestLocalSyncWorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return TestLocalSyncWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
