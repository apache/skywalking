package com.a.eye.skywalking.network.grpc.provider;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.SendResult;
import com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc;
import com.a.eye.skywalking.network.listener.SpanStorageListener;
import io.grpc.stub.StreamObserver;

public class SpanStorageService extends SpanStorageServiceGrpc.SpanStorageServiceImplBase {

    private SpanStorageListener listener;
    public SpanStorageService(SpanStorageListener listener) {
        this.listener = listener;
    }

    @Override
    public StreamObserver<AckSpan> storageACKSpan(final StreamObserver<SendResult> responseObserver) {
        return new StreamObserver<AckSpan>() {
            @Override
            public void onNext(AckSpan value) {
                listener.storage(value);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(SendResult.newBuilder().setResult(true).build());
                responseObserver.onCompleted();
            }
        };
    }


    @Override
    public StreamObserver<RequestSpan> storageRequestSpan(final StreamObserver<SendResult> responseObserver) {
        return new StreamObserver<RequestSpan>() {
            @Override
            public void onNext(RequestSpan value) {
                listener.storage(value);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(SendResult.newBuilder().setResult(true).build());
                responseObserver.onCompleted();
            }
        };
    }

}
