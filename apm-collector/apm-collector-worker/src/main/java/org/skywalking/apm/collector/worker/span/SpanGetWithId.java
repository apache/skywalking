package org.skywalking.apm.collector.worker.span;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.httpserver.AbstractGet;
import org.skywalking.apm.collector.worker.httpserver.AbstractGetProvider;
import org.skywalking.apm.collector.worker.span.persistence.SpanSearchWithId;
import org.skywalking.apm.collector.worker.tools.ParameterTools;

import java.util.Arrays;
import java.util.Map;

/**
 * @author pengys5
 */
public class SpanGetWithId extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(SpanGetWithId.class);

    SpanGetWithId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SpanSearchWithId.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onSearch(Map<String, String[]> request, JsonObject response) throws Exception {
        if (!request.containsKey("segId") || !request.containsKey("spanId")) {
            throw new IllegalArgumentException("the request parameter must contains segId, spanId");
        }
        logger.debug("segId: %s, spanId: %s", Arrays.toString(request.get("segId")), Arrays.toString(request.get("spanId")));

        String segId = ParameterTools.INSTANCE.toString(request, "segId");
        String spanId = ParameterTools.INSTANCE.toString(request, "spanId");

        SpanSearchWithId.RequestEntity requestEntity = new SpanSearchWithId.RequestEntity(segId, spanId);
        getSelfContext().lookup(SpanSearchWithId.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<SpanGetWithId> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SpanGetWithId workerInstance(ClusterWorkerContext clusterContext) {
            return new SpanGetWithId(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/span/spanId";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SpanGetWithId.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
