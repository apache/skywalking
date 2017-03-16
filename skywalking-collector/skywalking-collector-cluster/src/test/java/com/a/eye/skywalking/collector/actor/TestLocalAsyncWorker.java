package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestLocalAsyncWorker extends AbstractLocalAsyncWorker {

    public TestLocalAsyncWorker(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public void preStart() throws Exception {

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
        public Class<TestLocalAsyncWorker> workerClass() {
            return TestLocalAsyncWorker.class;
        }
    }

    public static class TestLocalASyncWorkerRole extends Role {
        public static TestLocalASyncWorkerRole INSTANCE = new TestLocalASyncWorkerRole();

        @Override
        public String name() {
            return TestLocalAsyncWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
