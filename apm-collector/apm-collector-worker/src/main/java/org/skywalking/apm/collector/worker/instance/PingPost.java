package org.skywalking.apm.collector.worker.instance;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
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
import org.skywalking.apm.collector.worker.httpserver.AbstractStreamPost;
import org.skywalking.apm.collector.worker.httpserver.AbstractStreamPostProvider;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;
import org.skywalking.apm.collector.worker.instance.analysis.PingTimeAnalysis;
import org.skywalking.apm.collector.worker.instance.entity.InstanceDeserialize;
import org.skywalking.apm.collector.worker.instance.entity.Ping;
import org.skywalking.apm.collector.worker.tools.DateTools;

public class PingPost extends AbstractStreamPost {

    public PingPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onReceive(BufferedReader reader,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        try {
            Ping ping = InstanceDeserialize.INSTANCE.deserializePingInfo(reader.readLine());
            if (ping == null || ping.getInstanceId() == -1) {
                throw new ArgumentsParseException("instance id required.");
            }
            getSelfContext().lookup(PingTimeAnalysis.Role.INSTANCE).tell(new PingTimeAnalysis.Ping(ping.getInstanceId(), DateTools.getMinuteSlice(System.currentTimeMillis())));
        } catch (IOException e) {
            throw new ArgumentsParseException(e.getMessage(), e);
        }
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(PingTimeAnalysis.Role.INSTANCE).create(this);
    }

    public static class Factory extends AbstractStreamPostProvider<PingPost> {
        @Override
        public Role role() {
            return PingPost.WorkerRole.INSTANCE;
        }

        @Override
        public PingPost workerInstance(ClusterWorkerContext clusterContext) {
            return new PingPost(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/instance/ping";
        }

    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return PingPost.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
