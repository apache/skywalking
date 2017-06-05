package org.skywalking.apm.collector.worker.instance;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerRef;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.httpserver.AbstractPost;
import org.skywalking.apm.collector.worker.httpserver.AbstractPostProvider;
import org.skywalking.apm.collector.worker.httpserver.AbstractPostWithHttpServlet;
import org.skywalking.apm.collector.worker.instance.entity.HeartBeat;
import org.skywalking.apm.collector.worker.instance.heartbeat.HeartBeatAnalysis;

public class HeartBeatReportPost extends AbstractPost {

    public HeartBeatReportPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(HeartBeatAnalysis.Role.INSTANCE).create(this);
    }

    @Override
    protected void onReceive(Object message, JsonObject response) throws Exception {
        if (message instanceof HeartBeat) {
            getSelfContext().lookup(HeartBeatAnalysis.Role.INSTANCE).tell(message);
        }
    }

    public static class Factory extends AbstractPostProvider<HeartBeatReportPost> {
        @Override
        public Role role() {
            return HeartBeatReportPost.WorkerRole.INSTANCE;
        }

        @Override
        public HeartBeatReportPost workerInstance(ClusterWorkerContext clusterContext) {
            return new HeartBeatReportPost(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/heartbeat";
        }

        @Override
        public AbstractPostWithHttpServlet handleServlet(WorkerRef workerRef) {
            return new HeartBeatPostWithHttpServlet(workerRef);
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return HeartBeatReportPost.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
