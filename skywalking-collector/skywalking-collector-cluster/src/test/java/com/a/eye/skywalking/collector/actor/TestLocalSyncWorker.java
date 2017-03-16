package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestLocalSyncWorker extends AbstractLocalSyncWorker {

    public TestLocalSyncWorker(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public void preStart() throws Exception {

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
        public Class<TestLocalSyncWorker> workerClass() {
            return TestLocalSyncWorker.class;
        }
    }

    public static class TestLocalSyncWorkerRole extends Role {
        public static TestLocalSyncWorkerRole INSTANCE = new TestLocalSyncWorkerRole();

        @Override
        public String name() {
            return TestLocalSyncWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
