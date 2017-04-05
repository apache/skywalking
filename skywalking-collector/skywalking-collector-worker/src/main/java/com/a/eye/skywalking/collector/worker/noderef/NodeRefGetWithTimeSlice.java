package com.a.eye.skywalking.collector.worker.noderef;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGet;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGetProvider;
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
public class NodeRefGetWithTimeSlice extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(NodeRefGetWithTimeSlice.class);

    private NodeRefGetWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(NodeRefSearchWithTimeSlice.WorkerRole.INSTANCE).create(this);
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

        NodeRefSearchWithTimeSlice.RequestEntity requestEntity;
        requestEntity = new NodeRefSearchWithTimeSlice.RequestEntity(ParameterTools.INSTANCE.toString(request, "timeSliceType"), startTime, endTime);
        getSelfContext().lookup(NodeRefSearchWithTimeSlice.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<NodeRefGetWithTimeSlice> {

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeRefGetWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefGetWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/nodeRef/timeSlice";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefGetWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
