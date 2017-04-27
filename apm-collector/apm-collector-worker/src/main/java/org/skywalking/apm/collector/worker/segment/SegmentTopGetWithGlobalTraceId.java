package org.skywalking.apm.collector.worker.segment;

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
import org.skywalking.apm.collector.worker.segment.persistence.SegmentTopSearchWithGlobalTraceId;
import org.skywalking.apm.collector.worker.tools.ParameterTools;

import java.util.Arrays;
import java.util.Map;

/**
 * @author pengys5
 */
public class SegmentTopGetWithGlobalTraceId extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(SegmentTopGetWithGlobalTraceId.class);

    SegmentTopGetWithGlobalTraceId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
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

        int from;
        try {
            from = Integer.valueOf(ParameterTools.INSTANCE.toString(request, "from"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter from must numeric with int type");
        }

        int limit;
        try {
            limit = Integer.valueOf(ParameterTools.INSTANCE.toString(request, "limit"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter from must numeric with int type");
        }

        String globalTraceId = ParameterTools.INSTANCE.toString(request, "globalTraceId");

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
