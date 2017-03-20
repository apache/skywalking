package com.a.eye.skywalking.collector.worker.dagnode.searcher;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.dagnode.persistence.NodeInstanceSearchPersistence;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGet;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractGetProvider;
import com.a.eye.skywalking.collector.worker.tools.ParameterTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Map;

/**
 * @author pengys5
 */
public class NodeInstanceWithTimeSliceGet extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(NodeInstanceWithTimeSliceGet.class);

    private NodeInstanceWithTimeSliceGet(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(NodeInstanceSearchPersistence.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onSearch(Map<String, String[]> request, JsonObject response) throws Exception {
        if (!request.containsKey("timeSliceValue") || !request.containsKey("timeSliceType")) {
            throw new IllegalArgumentException("the request parameter must contains timeSliceValue and timeSliceType");
        }
        logger.debug("timeSliceValue: %s, timeSliceType: %s", Arrays.toString(request.get("timeSliceValue")), Arrays.toString(request.get("timeSliceType")));

        long timeSlice;
        try {
            timeSlice = Long.valueOf(ParameterTools.toString(request, "timeSliceValue"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the request parameter timeSliceValue must numeric with long type");
        }

        NodeInstanceSearchPersistence.RequestEntity requestEntity;
        requestEntity = new NodeInstanceSearchPersistence.RequestEntity(ParameterTools.toString(request, "timeSliceType"), timeSlice);
        getSelfContext().lookup(NodeInstanceSearchPersistence.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<NodeInstanceWithTimeSliceGet> {

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeInstanceWithTimeSliceGet workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeInstanceWithTimeSliceGet(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/dagNode/search/nodeInstanceWithTimeSlice";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeInstanceWithTimeSliceGet.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
