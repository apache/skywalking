package org.skywalking.apm.collector.worker.noderef;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Map;
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
import org.skywalking.apm.collector.worker.httpserver.AbstractGet;
import org.skywalking.apm.collector.worker.httpserver.AbstractGetProvider;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;
import org.skywalking.apm.collector.worker.noderef.persistence.NodeRefResSumGroupWithTimeSlice;
import org.skywalking.apm.collector.worker.tools.ParameterTools;

/**
 * @author pengys5
 */
public class NodeRefResSumGetGroupWithTimeSlice extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumGetGroupWithTimeSlice.class);

    NodeRefResSumGetGroupWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override protected Class<? extends JsonElement> responseClass() {
        return JsonObject.class;
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(NodeRefResSumGroupWithTimeSlice.WorkerRole.INSTANCE).create(this);
    }

    @Override protected void onReceive(Map<String, String[]> parameter,
        JsonElement response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        if (!parameter.containsKey("startTime") || !parameter.containsKey("endTime") || !parameter.containsKey("timeSliceType")) {
            throw new ArgumentsParseException("the request parameter must contains startTime,endTime,timeSliceType");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("startTime: %s, endTime: %s, timeSliceType: %s", Arrays.toString(parameter.get("startTime")),
                Arrays.toString(parameter.get("endTime")), Arrays.toString(parameter.get("timeSliceType")));
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

        NodeRefResSumGroupWithTimeSlice.RequestEntity requestEntity;
        requestEntity = new NodeRefResSumGroupWithTimeSlice.RequestEntity(ParameterTools.INSTANCE.toString(parameter, "timeSliceType"), startTime, endTime);
        getSelfContext().lookup(NodeRefResSumGroupWithTimeSlice.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<NodeRefResSumGetGroupWithTimeSlice> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeRefResSumGetGroupWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefResSumGetGroupWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/nodeRef/resSum/groupTimeSlice";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumGetGroupWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
