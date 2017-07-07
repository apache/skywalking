package org.skywalking.apm.collector.worker.span;

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
import org.skywalking.apm.collector.worker.span.persistence.SpanSearchWithId;
import org.skywalking.apm.collector.worker.tools.ParameterTools;

/**
 * @author pengys5
 */
public class SpanGetWithId extends AbstractGet {

    private Logger logger = LogManager.getFormatterLogger(SpanGetWithId.class);

    SpanGetWithId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override protected Class<? extends JsonElement> responseClass() {
        return JsonObject.class;
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SpanSearchWithId.WorkerRole.INSTANCE).create(this);
    }

    @Override protected void onReceive(Map<String, String[]> parameter,
        JsonElement response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        if (!parameter.containsKey("segId") || !parameter.containsKey("spanId")) {
            throw new ArgumentsParseException("the request parameter must contains segId, spanId");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("segId: %s, spanId: %s", Arrays.toString(parameter.get("segId")), Arrays.toString(parameter.get("spanId")));
        }

        String segId = ParameterTools.INSTANCE.toString(parameter, "segId");
        String spanId = ParameterTools.INSTANCE.toString(parameter, "spanId");

        SpanSearchWithId.RequestEntity requestEntity = new SpanSearchWithId.RequestEntity(segId, spanId);
        getSelfContext().lookup(SpanSearchWithId.WorkerRole.INSTANCE).ask(requestEntity, response);
    }

    public static class Factory extends AbstractGetProvider<SpanGetWithId> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SpanGetWithId workerInstance(ClusterWorkerContext clusterContext) {
            return new SpanGetWithId(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/span/spanId";
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SpanGetWithId.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
