package org.apache.skywalking.oap.server.receiver.golang.provider.handler;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.GolangMetricCollection;
import org.apache.skywalking.apm.network.language.agent.v3.GolangMetricReportServiceGrpc;
import org.apache.skywalking.oap.server.analyzer.provider.golang.GolangSourceDispatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;


@Slf4j
public class GolangMetricHandler extends GolangMetricReportServiceGrpc.GolangMetricReportServiceImplBase implements GRPCHandler {

    private final GolangSourceDispatcher golangSourceDispatcher;
    private final NamingControl namingControl;


    public GolangMetricHandler(ModuleManager moduleManager) {
        this.golangSourceDispatcher = new GolangSourceDispatcher(moduleManager);
        this.namingControl = moduleManager.find(CoreModule.NAME)
                .provider()
                .getService(NamingControl.class);
    }
    @Override
    public void collect(GolangMetricCollection request, StreamObserver<Commands> responseObserver) {
        log.info(request.toString());
        final GolangMetricCollection.Builder builder = request.toBuilder();
        builder.setService(namingControl.formatServiceName(builder.getService()));
        builder.setServiceInstance(namingControl.formatInstanceName(builder.getServiceInstance()));

        builder.getMetricsList().forEach(golangMetric -> {
            golangSourceDispatcher.sendMetric(builder.getService(), builder.getServiceInstance(), golangMetric);
        });

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }
}