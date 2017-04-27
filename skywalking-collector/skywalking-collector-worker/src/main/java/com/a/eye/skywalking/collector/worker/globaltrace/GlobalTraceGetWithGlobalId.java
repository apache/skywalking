package com.a.eye.skywalking.collector.worker.globaltrace;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.globaltrace.persistence.GlobalTraceSearchWithGlobalId;
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
public class GlobalTraceGetWithGlobalId extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(GlobalTraceGetWithGlobalId.class);

    GlobalTraceGetWithGlobalId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onSearch(Map<String, String[]> request, JsonObject response) throws Exception {
        if (!request.containsKey("globalId")) {
            throw new IllegalArgumentException("the request parameter must contains globalId");
        }
        logger.debug("globalId: %s", Arrays.toString(request.get("globalId")));

        String globalId = ParameterTools.INSTANCE.toString(request, "globalId");

        getSelfContext().lookup(GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE).ask(globalId, response);
    }

    public static class Factory extends AbstractGetProvider<GlobalTraceGetWithGlobalId> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public GlobalTraceGetWithGlobalId workerInstance(ClusterWorkerContext clusterContext) {
            return new GlobalTraceGetWithGlobalId(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/globalTrace/globalId";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return GlobalTraceGetWithGlobalId.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
