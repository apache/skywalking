package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGet;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGetProvider;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentTopSearchWithTimeSlice;
import com.a.eye.skywalking.collector.worker.tools.ParameterTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Map;

/**
 * @author pengys5
 */
public class SegmentTopGetWithTimeSlice extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(SegmentTopGetWithTimeSlice.class);

    private SegmentTopGetWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentTopSearchWithTimeSlice.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onSearch(Map<String, String[]> request, JsonObject response) throws Exception {
        if (!request.containsKey("startTime") || !request.containsKey("endTime") || !request.containsKey("from") || !request.containsKey("limit")) {
            throw new IllegalArgumentException("the request parameter must contains startTime, endTime, from, limit");
        }
        logger.debug("startTime: %s, endTime: %s, from: %s", Arrays.toString(request.get("startTime")),
                Arrays.toString(request.get("endTime")), Arrays.toString(request.get("from")));

        long startTime;
        try {
            startTime = Long.valueOf(ParameterTools.INSTANCE.toString(request, "startTime"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter startTime must numeric with long type");
        }

        long endTime;
        try {
            endTime = Long.valueOf(ParameterTools.INSTANCE.toString(request, "endTime"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter endTime must numeric with long type");
        }

        int from = 0;
        try {
            from = Integer.valueOf(ParameterTools.INSTANCE.toString(request, "from"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter from must numeric with int type");
        }

        int limit = 0;
        try {
            limit = Integer.valueOf(ParameterTools.INSTANCE.toString(request, "limit"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter from must numeric with int type");
        }

        int minCost = -1;
        if (request.containsKey("minCost")) {
            minCost = Integer.valueOf(ParameterTools.INSTANCE.toString(request, "minCost"));
        }
        int maxCost = -1;
        if (request.containsKey("maxCost")) {
            maxCost = Integer.valueOf(ParameterTools.INSTANCE.toString(request, "maxCost"));
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
