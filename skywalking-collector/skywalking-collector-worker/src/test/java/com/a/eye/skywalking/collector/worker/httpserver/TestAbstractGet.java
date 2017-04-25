package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * @author pengys5
 */
public class TestAbstractGet extends AbstractGet {
    protected TestAbstractGet(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override
    protected void onSearch(Map<String, String[]> request, JsonObject response) throws Exception {

    }

    public static class Factory extends AbstractGetProvider<TestAbstractGet> {
        @Override
        public Role role() {
            return TestAbstractGet.WorkerRole.INSTANCE;
        }

        @Override
        public TestAbstractGet workerInstance(ClusterWorkerContext clusterContext) {
            return new TestAbstractGet(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/TestAbstractGet";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return TestAbstractGet.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
