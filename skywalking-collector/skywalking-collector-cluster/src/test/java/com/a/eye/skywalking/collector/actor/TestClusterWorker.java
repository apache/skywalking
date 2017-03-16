package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestClusterWorker extends AbstractClusterWorker {

    public TestClusterWorker(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public void preStart() throws Exception {
        getClusterContext().findProvider(TestLocalSyncWorker.TestLocalSyncWorkerRole.INSTANCE).create(getClusterContext(), getSelfContext());
        getClusterContext().findProvider(TestLocalAsyncWorker.TestLocalASyncWorkerRole.INSTANCE).create(getClusterContext(), getSelfContext());
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
            return new TestClusterWorkerRole();
        }

        @Override
        public Class<TestClusterWorker> workerClass() {
            return TestClusterWorker.class;
        }
    }

    public static class TestClusterWorkerRole extends Role {
        public static TestClusterWorkerRole INSTANCE = new TestClusterWorkerRole();

        @Override
        public String name() {
            return TestClusterWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}