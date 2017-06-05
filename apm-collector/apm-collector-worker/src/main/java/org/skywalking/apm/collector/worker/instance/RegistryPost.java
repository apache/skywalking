package org.skywalking.apm.collector.worker.instance;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.skywalking.apm.collector.worker.instance.entity.InstanceInfo;
import org.skywalking.apm.collector.worker.instance.entity.RegistryInfo;

public class RegistryPost extends AbstractPost {

    private IdentificationCache instanceIDCache;
    private Logger logger = LogManager.getLogger(RegistryPost.class);

    public RegistryPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        instanceIDCache = IdentificationCache.initCache();
        getClusterContext().findProvider(InstanceInfoSave.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onReceive(Object requestParam, JsonObject response) throws Exception {
        if (requestParam instanceof RegistryInfo) {
            try {
                long instanceId = instanceIDCache.fetchInstanceId();

                getSelfContext().lookup(InstanceInfoSave.WorkerRole.INSTANCE).ask(new
                    InstanceInfo(((RegistryInfo)requestParam).getApplicationCode(), instanceId), response);

                response.addProperty("ii", instanceId);
            } catch (Exception e) {
                logger.error("Register failure.", e);
                response.addProperty("ii", -1);
            }
        }
    }

    public static class Factory extends AbstractPostProvider<RegistryPost> {
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

        @Override
        public AbstractPostWithHttpServlet handleServlet(WorkerRef workerRef) {
            return new RegisterPostWithHttpServlet(workerRef);
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
