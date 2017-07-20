package org.skywalking.apm.collector.agentjvm.grpc.handler;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.JVMMetrics;
import org.skywalking.apm.network.proto.JVMMetricsServiceGrpc;

/**
 * @author pengys5
 */
public class JVMMetricsServiceHandler extends JVMMetricsServiceGrpc.JVMMetricsServiceImplBase implements GRPCHandler {

    @Override public void collect(JVMMetrics request, StreamObserver<Downstream> responseObserver) {
        super.collect(request, responseObserver);
    }
}
