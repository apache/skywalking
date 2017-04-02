package com.a.eye.skywalking.collector.worker.span;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGet;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGetProvider;
import com.a.eye.skywalking.collector.worker.span.persistence.SpanSearchWithId;
import com.a.eye.skywalking.collector.worker.tools.ParameterTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Map;

/**
 * @author pengys5
 */
public class SpanGetWithId extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(SpanGetWithId.class);

    private SpanGetWithId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
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

        int maxCost = -1;
        if (request.containsKey("maxCost")) {
            maxCost = Integer.valueOf(ParameterTools.toString(request, "maxCost"));
        }

        String segId = ParameterTools.toString(request, "segId");
        String spanId = ParameterTools.toString(request, "spanId");

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
