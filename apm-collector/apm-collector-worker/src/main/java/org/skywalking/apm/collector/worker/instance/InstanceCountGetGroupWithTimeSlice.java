package org.skywalking.apm.collector.worker.instance;

import com.google.gson.JsonObject;
import java.util.Map;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.httpserver.AbstractGet;
import org.skywalking.apm.collector.worker.httpserver.AbstractGetProvider;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;
import org.skywalking.apm.collector.worker.instance.persistence.InstanceCountSearchGroupWithTimeSlice;
import org.skywalking.apm.collector.worker.tools.ParameterTools;

public class InstanceCountGetGroupWithTimeSlice extends AbstractGet {
    protected InstanceCountGetGroupWithTimeSlice(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(InstanceCountSearchGroupWithTimeSlice.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onReceive(Map<String, String[]> parameter,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        if (!parameter.containsKey("startTime") || !parameter.containsKey("endTime")) {
            throw new ArgumentsParseException("the request parameter must contains startTime,endTime");
        }

        long startTime;
        try {
            startTime = Long.valueOf(ParameterTools.INSTANCE.toString(parameter, "startTime"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter startTime must be a long");
        }

        long endTime;
        try {
            endTime = Long.valueOf(ParameterTools.INSTANCE.toString(parameter, "endTime"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter endTime must be a long");
        }

        InstanceCountSearchGroupWithTimeSlice.RequestEntity requestEntity;
        requestEntity = new InstanceCountSearchGroupWithTimeSlice.RequestEntity(startTime, endTime);
        getSelfContext().lookup(InstanceCountSearchGroupWithTimeSlice.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<InstanceCountGetGroupWithTimeSlice> {
        @Override
        public Role role() {
            return InstanceCountGetGroupWithTimeSlice.WorkerRole.INSTANCE;
        }

        @Override
        public InstanceCountGetGroupWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new InstanceCountGetGroupWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/instances/counts";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return InstanceCountGetGroupWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
