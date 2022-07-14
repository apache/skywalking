package org.apache.skywalking.oap.server.receiver.golang.provider.handler;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.GolangMetricCollection;
import org.apache.skywalking.apm.network.language.agent.v3.GolangMetricReportServiceGrpc;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;


@Slf4j
public class GolangMetricHandler extends GolangMetricReportServiceGrpc.GolangMetricReportServiceImplBase implements GRPCHandler {


    @Override
    public void collect(GolangMetricCollection request, StreamObserver<Commands> responseObserver) {
        log.info(request.toString());
        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }
}
