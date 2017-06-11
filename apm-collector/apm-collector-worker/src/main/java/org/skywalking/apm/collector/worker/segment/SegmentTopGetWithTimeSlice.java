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
import org.skywalking.apm.collector.worker.segment.persistence.SegmentTopSearchWithTimeSlice;
import org.skywalking.apm.collector.worker.tools.ParameterTools;

/**
 * @author pengys5
 */
public class SegmentTopGetWithTimeSlice extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(SegmentTopGetWithTimeSlice.class);

    SegmentTopGetWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentTopSearchWithTimeSlice.WorkerRole.INSTANCE).create(this);
    }

    @Override protected void onReceive(Map<String, String[]> parameter, JsonObject response) throws Exception {
        if (!parameter.containsKey("startTime") || !parameter.containsKey("endTime") || !parameter.containsKey("from") || !parameter.containsKey("limit")) {
            throw new IllegalArgumentException("the request parameter must contains startTime, endTime, from, limit");
        }
        logger.debug("startTime: %s, endTime: %s, from: %s", Arrays.toString(parameter.get("startTime")),
            Arrays.toString(parameter.get("endTime")), Arrays.toString(parameter.get("from")));

        long startTime;
        try {
            startTime = Long.valueOf(ParameterTools.INSTANCE.toString(parameter, "startTime"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter startTime must numeric with long type");
        }

        long endTime;
        try {
            endTime = Long.valueOf(ParameterTools.INSTANCE.toString(parameter, "endTime"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter endTime must numeric with long type");
        }

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

        int minCost = -1;
        if (parameter.containsKey("minCost")) {
            minCost = Integer.valueOf(ParameterTools.INSTANCE.toString(parameter, "minCost"));
        }
        int maxCost = -1;
        if (parameter.containsKey("maxCost")) {
            maxCost = Integer.valueOf(ParameterTools.INSTANCE.toString(parameter, "maxCost"));
        }

        SegmentTopSearchWithTimeSlice.RequestEntity requestEntity;
        requestEntity = new SegmentTopSearchWithTimeSlice.RequestEntity(from, limit, startTime, endTime);
        requestEntity.setMinCost(minCost);
        requestEntity.setMaxCost(maxCost);
        getSelfContext().lookup(SegmentTopSearchWithTimeSlice.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<SegmentTopGetWithTimeSlice> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentTopGetWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentTopGetWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/segments/top/timeSlice";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentTopGetWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
