package com.a.eye.skywalking.collector.worker.tracedag;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGet;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGetProvider;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeCompLoad;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeMappingSearchWithTimeSlice;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefResSumSearchWithTimeSlice;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefSearchWithTimeSlice;
import com.a.eye.skywalking.collector.worker.tools.ParameterTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Map;

/**
 * @author pengys5
 */
public class TraceDagGetWithTimeSlice extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(TraceDagGetWithTimeSlice.class);

    TraceDagGetWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(NodeCompLoad.WorkerRole.INSTANCE).create(this);
        getClusterContext().findProvider(NodeMappingSearchWithTimeSlice.WorkerRole.INSTANCE).create(this);
        getClusterContext().findProvider(NodeRefSearchWithTimeSlice.WorkerRole.INSTANCE).create(this);
        getClusterContext().findProvider(NodeRefResSumSearchWithTimeSlice.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onSearch(Map<String, String[]> request, JsonObject response) throws Exception {
        if (!request.containsKey("startTime") || !request.containsKey("endTime") || !request.containsKey("timeSliceType")) {
            throw new IllegalArgumentException("the request parameter must contains startTime,endTime,timeSliceType");
        }
        logger.debug("startTime: %s, endTime: %s, timeSliceType: %s", Arrays.toString(request.get("startTime")),
                Arrays.toString(request.get("endTime")), Arrays.toString(request.get("timeSliceType")));

        long startTime;
        try {
            startTime = Long.valueOf(ParameterTools.toString(request, "startTime"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter startTime must numeric with long type");
        }

        long endTime;
        try {
            endTime = Long.valueOf(ParameterTools.toString(request, "endTime"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter endTime must numeric with long type");
        }

        String timeSliceType = ParameterTools.toString(request, "timeSliceType");

        JsonObject compResponse = new JsonObject();
        getSelfContext().lookup(NodeCompLoad.WorkerRole.INSTANCE).ask(null, compResponse);

        JsonObject nodeMappingResponse = new JsonObject();
        NodeMappingSearchWithTimeSlice.RequestEntity nodeMappingEntity = new NodeMappingSearchWithTimeSlice.RequestEntity(timeSliceType, startTime, endTime);
        getSelfContext().lookup(NodeMappingSearchWithTimeSlice.WorkerRole.INSTANCE).ask(nodeMappingEntity, nodeMappingResponse);

        JsonObject nodeRefResponse = new JsonObject();
        NodeRefSearchWithTimeSlice.RequestEntity nodeReftEntity = new NodeRefSearchWithTimeSlice.RequestEntity(timeSliceType, startTime, endTime);
        getSelfContext().lookup(NodeRefSearchWithTimeSlice.WorkerRole.INSTANCE).ask(nodeReftEntity, nodeRefResponse);

        JsonObject resSumResponse = new JsonObject();
        NodeRefResSumSearchWithTimeSlice.RequestEntity resSumEntity = new NodeRefResSumSearchWithTimeSlice.RequestEntity(timeSliceType, startTime, endTime);
        getSelfContext().lookup(NodeRefResSumSearchWithTimeSlice.WorkerRole.INSTANCE).ask(resSumEntity, resSumResponse);

        TraceDagDataBuilder builder = new TraceDagDataBuilder();
        JsonObject result = builder.build(compResponse.get(Const.RESULT).getAsJsonArray(), nodeMappingResponse.get(Const.RESULT).getAsJsonArray(),
                nodeRefResponse.get(Const.RESULT).getAsJsonArray(), resSumResponse.get(Const.RESULT).getAsJsonArray());

        response.add(Const.RESULT, result);
    }

    public static class Factory extends AbstractGetProvider<TraceDagGetWithTimeSlice> {

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public TraceDagGetWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new TraceDagGetWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/traceDag/timeSlice";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return TraceDagGetWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
