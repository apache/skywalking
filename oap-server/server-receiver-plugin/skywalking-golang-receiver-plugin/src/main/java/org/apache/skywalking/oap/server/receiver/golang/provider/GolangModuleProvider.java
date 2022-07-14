package org.apache.skywalking.oap.server.receiver.golang.provider;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.golang.module.GolangModule;
import org.apache.skywalking.oap.server.receiver.golang.provider.handler.GolangMetricHandler;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

public class GolangModuleProvider extends ModuleProvider {
    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return GolangModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return null;
    }

    @Override
    public void prepare() {
    }

    @Override
    public void start() throws ModuleStartException {
        // load official analysis
//        getManager().find(CoreModule.NAME)
//                .provider()
//                .getService(OALEngineLoaderService.class)
//                .load(GolangOALDefine.INSTANCE);

        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                .provider()
                .getService(GRPCHandlerRegister.class);
        GolangMetricHandler golangMetricHandler = new GolangMetricHandler();
        grpcHandlerRegister.addHandler(golangMetricHandler);
    }

    @Override
    public void notifyAfterCompleted() {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
                CoreModule.NAME,
                SharingServerModule.NAME
        };
    }
}
