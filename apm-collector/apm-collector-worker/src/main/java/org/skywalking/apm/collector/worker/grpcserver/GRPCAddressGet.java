package org.skywalking.apm.collector.worker.grpcserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Map;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.httpserver.AbstractGet;
import org.skywalking.apm.collector.worker.httpserver.AbstractGetProvider;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;

/**
 * @author pengys5
 */
public class GRPCAddressGet extends AbstractGet {

    protected GRPCAddressGet(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override protected Class<JsonArray> responseClass() {
        return JsonArray.class;
    }

    @Override protected void onReceive(Map<String, String[]> parameter,
        JsonElement response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        ((ClusterWorkerContext)getClusterContext()).getRpcContext().rpcAddressCollection().forEach(rpcAddress -> {
            ((JsonArray)response).add(rpcAddress.getAddress() + ":" + rpcAddress.getPort());
        });
    }

    public static class Factory extends AbstractGetProvider<GRPCAddressGet> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public GRPCAddressGet workerInstance(ClusterWorkerContext clusterContext) {
            return new GRPCAddressGet(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public String servletPath() {
            return "/grpc/addresses";
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return GRPCAddressGet.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
