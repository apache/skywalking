package org.skywalking.apm.collector.worker.segment;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Map;
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

    @Override protected void onReceive(Map<String, String[]> parameter, JsonObject response) throws Exception {
        if (!parameter.containsKey("globalTraceId") || !parameter.containsKey("from") || !parameter.containsKey("limit")) {
            throw new IllegalArgumentException("the request parameter must contains globalTraceId, from, limit");
        }
        logger.debug("globalTraceId: %s, from: %s, limit: %s", Arrays.toString(parameter.get("globalTraceId")),
            Arrays.toString(parameter.get("from")), Arrays.toString(parameter.get("limit")));

        int from;
        try {
            from = Integer.valueOf(ParameterTools.INSTANCE.toString(parameter, "from"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter from must numeric with int type");
        }

        int limit;
        try {
            limit = Integer.valueOf(ParameterTools.INSTANCE.toString(parameter, "limit"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter from must numeric with int type");
        }

        String globalTraceId = ParameterTools.INSTANCE.toString(parameter, "globalTraceId");

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
