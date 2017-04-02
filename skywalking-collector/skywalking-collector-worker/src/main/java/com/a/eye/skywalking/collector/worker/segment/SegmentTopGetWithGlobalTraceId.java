package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGet;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGetProvider;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentTopSearchWithGlobalTraceId;
import com.a.eye.skywalking.collector.worker.tools.ParameterTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Map;

/**
 * @author pengys5
 */
public class SegmentTopGetWithGlobalTraceId extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(SegmentTopGetWithGlobalTraceId.class);

    private SegmentTopGetWithGlobalTraceId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentTopSearchWithGlobalTraceId.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onSearch(Map<String, String[]> request, JsonObject response) throws Exception {
        if (!request.containsKey("globalTraceId") || !request.containsKey("from") || !request.containsKey("limit")) {
            throw new IllegalArgumentException("the request parameter must contains globalTraceId, from, limit");
        }
        logger.debug("globalTraceId: %s, from: %s, limit: %s", Arrays.toString(request.get("globalTraceId")),
                Arrays.toString(request.get("from")), Arrays.toString(request.get("limit")));

        int from = 0;
        try {
            from = Integer.valueOf(ParameterTools.toString(request, "from"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter from must numeric with int type");
        }

        int limit = 0;
        try {
            limit = Integer.valueOf(ParameterTools.toString(request, "limit"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter from must numeric with int type");
        }

        String globalTraceId = ParameterTools.toString(request, "globalTraceId");

        SegmentTopSearchWithGlobalTraceId.RequestEntity requestEntity = new SegmentTopSearchWithGlobalTraceId.RequestEntity(globalTraceId, from, limit);
        getSelfContext().lookup(SegmentTopSearchWithGlobalTraceId.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<SegmentTopGetWithGlobalTraceId> {

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentTopGetWithGlobalTraceId workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentTopGetWithGlobalTraceId(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/segments/top/globalTraceId";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentTopGetWithGlobalTraceId.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
