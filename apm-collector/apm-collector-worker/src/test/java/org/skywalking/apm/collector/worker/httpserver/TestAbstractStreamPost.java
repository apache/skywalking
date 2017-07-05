package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestAbstractStreamPost extends AbstractStreamPost {
    public TestAbstractStreamPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override protected Class<? extends JsonElement> responseClass() {
        return JsonObject.class;
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected void onReceive(BufferedReader reader,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {

    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return TestAbstractStreamPost.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }

    public static class Factory extends AbstractStreamPostProvider<TestAbstractStreamPost> {
        @Override
        public String servletPath() {
            return "/TestAbstractPost";
        }

        @Override
        public Role role() {
            return TestAbstractStreamPost.WorkerRole.INSTANCE;
        }

        @Override
        public TestAbstractStreamPost workerInstance(ClusterWorkerContext clusterContext) {
            return new TestAbstractStreamPost(role(), clusterContext, new LocalWorkerContext());
        }
    }
}
