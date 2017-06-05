package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;

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
