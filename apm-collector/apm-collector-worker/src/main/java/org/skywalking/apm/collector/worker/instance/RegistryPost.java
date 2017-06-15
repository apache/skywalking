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
import org.skywalking.apm.collector.worker.instance.analysis.InstanceAnalysis;
import org.skywalking.apm.collector.worker.instance.entity.InstanceDeserialize;
import org.skywalking.apm.collector.worker.instance.entity.RegistryInfo;
import org.skywalking.apm.collector.worker.instance.util.IDSequence;
import org.skywalking.apm.collector.worker.tools.DateTools;
import org.skywalking.apm.util.StringUtil;

public class RegistryPost extends AbstractStreamPost {

    private Logger logger = LogManager.getFormatterLogger(RegistryPost.class);

    public RegistryPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onReceive(BufferedReader reader,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        try {
            long instanceId = IDSequence.INSTANCE.fetchInstanceId();
            RegistryInfo registryInfo = InstanceDeserialize.INSTANCE.deserializeRegistryInfo(reader.readLine());

            validateRegistryInfo(registryInfo);

            InstanceInfo instanceInfo = new InstanceInfo(registryInfo, instanceId);
            getSelfContext().lookup(InstanceAnalysis.Role.INSTANCE).tell(instanceInfo);
            response.addProperty("ii", instanceInfo.getInstanceId());
            response.addProperty("pt", instanceInfo.getRegistryTime());
        } catch (Exception e) {
            logger.error("Register failure.", e);
            response.addProperty("ii", "-1");
        }
    }

    private void validateRegistryInfo(RegistryInfo info) throws IllegalArgumentException {
        if (StringUtil.isEmpty(info.getApplicationCode())) {
            throw new IllegalArgumentException("application code is empty");
        }
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(InstanceAnalysis.Role.INSTANCE).create(this);
    }

    public static class InstanceInfo {
        private String applicationCode;
        private long instanceId;
        private long registryTime;
        private long lastPingTime;

        public InstanceInfo(RegistryInfo registryInfo, long instanceId) {
            this.applicationCode = registryInfo.getApplicationCode();
            this.instanceId = instanceId;
            registryTime = DateTools.getMinuteSlice(System.currentTimeMillis());
            lastPingTime = registryTime;
        }

        public JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("ac", applicationCode);
            jsonObject.addProperty("ii", instanceId);
            jsonObject.addProperty("rt", registryTime);
            jsonObject.addProperty("pt", lastPingTime);
            return jsonObject;
        }

        public long getRegistryTime() {
            return registryTime;
        }

        public long getInstanceId() {
            return instanceId;
        }
    }

    public static class Factory extends AbstractStreamPostProvider<RegistryPost> {
        @Override
        public Role role() {
            return RegistryPost.WorkerRole.INSTANCE;
        }

        @Override
        public RegistryPost workerInstance(ClusterWorkerContext clusterContext) {
            return new RegistryPost(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/register";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return RegistryPost.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
