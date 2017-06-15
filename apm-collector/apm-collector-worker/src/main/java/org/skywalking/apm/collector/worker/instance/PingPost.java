package org.skywalking.apm.collector.worker.instance;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
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
import org.skywalking.apm.collector.worker.instance.entity.PingInfo;
import org.skywalking.apm.collector.worker.tools.DateTools;

public class PingPost extends AbstractStreamPost {

    private Logger logger = LogManager.getFormatterLogger(PingPost.class);

    public PingPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onReceive(BufferedReader reader,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        try {
            PingInfo pingInfo = InstanceDeserialize.INSTANCE.deserializePingInfo(reader.readLine());
            validatePingInfo(pingInfo);
            getSelfContext().lookup(PingTimeAnalysis.Role.INSTANCE).tell(new Ping(pingInfo));
        } catch (Exception e) {
            logger.error("Failed to save ping data.", e);
        }
    }

    private void validatePingInfo(PingInfo info) {
        if (info == null || info.getInstanceId() == -1) {
            throw new IllegalArgumentException("Cannot serialize ping info.");
        }
    }

    public static class Ping {
        private long instanceId;
        private long pingTime;

        public Ping(PingInfo pingInfo) {
            this.instanceId = pingInfo.getInstanceId();
            this.pingTime = DateTools.getMinuteSlice(System.currentTimeMillis());
        }

        public long getInstanceId() {
            return instanceId;
        }

        public long getPingTime() {
            return pingTime;
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
            return "/ping";
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
