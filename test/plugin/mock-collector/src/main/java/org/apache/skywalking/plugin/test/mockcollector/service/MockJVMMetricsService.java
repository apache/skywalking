package org.apache.skywalking.plugin.test.mockcollector.service;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.language.agent.Downstream;
import org.apache.skywalking.apm.network.language.agent.JVMMetrics;
import org.apache.skywalking.apm.network.language.agent.JVMMetricsServiceGrpc;

/**
 * Created by xin on 2017/7/11.
 */
public class MockJVMMetricsService extends JVMMetricsServiceGrpc.JVMMetricsServiceImplBase {
    @Override
    public void collect(JVMMetrics request, StreamObserver<Downstream> responseObserver) {
        responseObserver.onNext(Downstream.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
