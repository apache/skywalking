package com.a.eye.skywalking.collector.actor;


import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestClusterWorker extends AbstractClusterWorker {

    public TestClusterWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFountException {
        getClusterContext().findProvider(TestLocalSyncWorker.TestLocalSyncWorkerRole.INSTANCE).create(this);
        getClusterContext().findProvider(TestLocalAsyncWorker.TestLocalASyncWorkerRole.INSTANCE).create(this);
    }

    @Override
    public void work(Object message) throws Exception {
        if (message.equals("Print")) {
            System.out.println(message);
        } else if (message.equals("TellLocalWorker")) {
            System.out.println(message);
            getSelfContext().lookup(TestLocalSyncWorker.TestLocalSyncWorkerRole.INSTANCE).tell(message);
        } else if (message.equals("TellLocalAsyncWorker")) {
            System.out.println(message);
            getSelfContext().lookup(TestLocalAsyncWorker.TestLocalASyncWorkerRole.INSTANCE).tell(message);
        } else {
            System.out.println("unhandled");
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<TestClusterWorker> {

        @Override
        public int workerNum() {
            return 5;
        }

        @Override
        public Role role() {
            return TestClusterWorkerRole.INSTANCE;
        }

        @Override
        public TestClusterWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new TestClusterWorker(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum TestClusterWorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return TestClusterWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}