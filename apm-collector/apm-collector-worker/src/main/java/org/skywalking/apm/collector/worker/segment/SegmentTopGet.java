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
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.httpserver.AbstractGet;
import org.skywalking.apm.collector.worker.httpserver.AbstractGetProvider;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;
import org.skywalking.apm.collector.worker.segment.persistence.SegmentTopSearch;
import org.skywalking.apm.collector.worker.tools.ParameterTools;

/**
 * @author pengys5
 */
public class SegmentTopGet extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(SegmentTopGet.class);

    SegmentTopGet(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentTopSearch.WorkerRole.INSTANCE).create(this);
    }

    @Override protected void onReceive(Map<String, String[]> parameter,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        if (!parameter.containsKey("startTime") || !parameter.containsKey("endTime") || !parameter.containsKey("from") || !parameter.containsKey("limit")) {
            throw new ArgumentsParseException("the request parameter must contains startTime, endTime, from, limit");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("startTime: %s, endTime: %s, from: %s", Arrays.toString(parameter.get("startTime")),
                Arrays.toString(parameter.get("endTime")), Arrays.toString(parameter.get("from")));
        }

        long startTime;
        try {
            startTime = Long.valueOf(ParameterTools.INSTANCE.toString(parameter, "startTime"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter startTime must be a long");
        }

        long endTime;
        try {
            endTime = Long.valueOf(ParameterTools.INSTANCE.toString(parameter, "endTime"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter endTime must be a long");
        }

        int from;
        try {
            from = Integer.valueOf(ParameterTools.INSTANCE.toString(parameter, "from"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter from must be an integer");
        }

        int limit;
        try {
            limit = Integer.valueOf(ParameterTools.INSTANCE.toString(parameter, "limit"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter from must be an integer");
        }

        int minCost = -1;
        if (parameter.containsKey("minCost")) {
            minCost = Integer.valueOf(ParameterTools.INSTANCE.toString(parameter, "minCost"));
        }
        int maxCost = -1;
        if (parameter.containsKey("maxCost")) {
            maxCost = Integer.valueOf(ParameterTools.INSTANCE.toString(parameter, "maxCost"));
        }

        String globalTraceId = null;
        if (parameter.containsKey("globalTraceId")) {
            globalTraceId = ParameterTools.INSTANCE.toString(parameter, "globalTraceId");
        }

        String operationName = null;
        if (parameter.containsKey("operationName")) {
            operationName = ParameterTools.INSTANCE.toString(parameter, "operationName");
        }

        SegmentTopSearch.RequestEntity requestEntity;
        requestEntity = new SegmentTopSearch.RequestEntity(from, limit, startTime, endTime, globalTraceId, operationName);
        requestEntity.setMinCost(minCost);
        requestEntity.setMaxCost(maxCost);
        getSelfContext().lookup(SegmentTopSearch.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<SegmentTopGet> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentTopGet workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentTopGet(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/segments/top";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentTopGet.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
