package org.skywalking.apm.collector.worker.grpcserver;

import org.skywalking.apm.collector.actor.AbstractClusterWorker;
import org.skywalking.apm.collector.actor.AbstractClusterWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.rpc.RPCAddress;
import org.skywalking.apm.collector.worker.config.GRPCConfig;
import org.skywalking.apm.collector.worker.config.WorkerConfig;

/**
 * @author pengys5
 */
public class GRPCAddressRegister extends AbstractClusterWorker {

    GRPCAddressRegister(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {

    }

    @Override protected void onWork(Object message) throws WorkerException {
    }

    public static class Factory extends AbstractClusterWorkerProvider<GRPCAddressRegister> {
        public static Factory INSTANCE = new Factory();

        @Override
        public GRPCAddressRegister.Role role() {
            return Role.INSTANCE;
        }

        @Override public int workerNum() {
            return WorkerConfig.WorkerNum.GRPC.GRPCAddressRegister.VALUE;
        }

        @Override public RPCAddress config() {
            return new RPCAddress(GRPCConfig.GRPC.HOSTNAME, Integer.valueOf(GRPCConfig.GRPC.PORT));
        }

        @Override public GRPCAddressRegister workerInstance(ClusterWorkerContext clusterContext) {
            return new GRPCAddressRegister(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return GRPCAddressRegister.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
