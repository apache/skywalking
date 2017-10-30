package org.skywalking.apm.collector.remote.grpc;

import java.util.Properties;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.remote.RemoteModule;
import org.skywalking.apm.collector.remote.grpc.service.GRPCDataService;
import org.skywalking.apm.collector.remote.service.DataService;

/**
 * @author peng-yongsheng
 */
public class RemoteModuleGRPCProvider extends ModuleProvider {

    @Override public String name() {
        return "gRPC";
    }

    @Override public Class<? extends Module> module() {
        return RemoteModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        this.registerServiceImplementation(DataService.class, new GRPCDataService());
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {

    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
