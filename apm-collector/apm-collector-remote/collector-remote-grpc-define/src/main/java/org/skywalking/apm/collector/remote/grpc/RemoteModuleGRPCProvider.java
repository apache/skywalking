package org.skywalking.apm.collector.remote.grpc;

import java.util.Properties;
import org.skywalking.apm.collector.cluster.ClusterModule;
import org.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleNotFoundException;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.grpc.manager.GRPCManagerModule;
import org.skywalking.apm.collector.grpc.manager.service.GRPCManagerService;
import org.skywalking.apm.collector.remote.RemoteDataMappingContainer;
import org.skywalking.apm.collector.remote.RemoteModule;
import org.skywalking.apm.collector.remote.grpc.data.instance.InstPerformanceData;
import org.skywalking.apm.collector.remote.grpc.handler.RemoteCommonServiceHandler;
import org.skywalking.apm.collector.remote.grpc.service.GRPCRemoteClientService;
import org.skywalking.apm.collector.remote.grpc.service.GRPCRemoteServerService;
import org.skywalking.apm.collector.remote.service.DataReceiverRegisterListener;
import org.skywalking.apm.collector.remote.service.RemoteClientService;
import org.skywalking.apm.collector.remote.service.RemoteServerService;
import org.skywalking.apm.collector.server.Server;

/**
 * @author peng-yongsheng
 */
public class RemoteModuleGRPCProvider extends ModuleProvider {

    private static final String HOST = "host";
    private static final String PORT = "port";
    private RemoteDataMappingContainer container;
    private final DataReceiverRegisterListener listener = new DataReceiverRegisterListener();

    @Override public String name() {
        return "gRPC";
    }

    @Override public Class<? extends Module> module() {
        return RemoteModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        container = loadMapping();
        this.registerServiceImplementation(RemoteServerService.class, new GRPCRemoteServerService(listener));
        this.registerServiceImplementation(RemoteClientService.class, new GRPCRemoteClientService(container));
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        String host = config.getProperty(HOST);
        Integer port = (Integer)config.get(PORT);

        try {
            GRPCManagerService managerService = getManager().find(GRPCManagerModule.NAME).getService(GRPCManagerService.class);
            Server gRPCServer = managerService.getOrCreateIfAbsent(host, port);
            gRPCServer.addHandler(new RemoteCommonServiceHandler(container, listener));

            ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
            moduleRegisterService.register(RemoteModule.NAME, this.name(), new RemoteModuleGRPCRegistration(host, port));
        } catch (ModuleNotFoundException e) {
            throw new ServiceNotProvidedException(e.getMessage());
        }
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {ClusterModule.NAME, GRPCManagerModule.NAME};
    }

    private RemoteDataMappingContainer loadMapping() {
        RemoteDataMappingContainer container = new RemoteDataMappingContainer();
        container.addMapping(new InstPerformanceData());
        return container;
    }
}
