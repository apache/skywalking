package org.skywalking.apm.collector.agentregister.grpc.handler;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;

/**
 * @author pengys5
 */
public class ApplicationRegisterServiceHandler extends ApplicationRegisterServiceGrpc.ApplicationRegisterServiceImplBase implements GRPCHandler {
    @Override public void register(Application request, StreamObserver<ApplicationMapping> responseObserver) {

    }
}
