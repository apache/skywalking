package com.a.eye.skywalking.network.grpc.client;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.SendResult;
import com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;
import io.grpc.ManagedChannel;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.locks.LockSupport;

public class SpanStorageClient {

    private final SpanStorageServiceGrpc.SpanStorageServiceStub spanStorageStub;

    private final StorageClientListener listener;

    public SpanStorageClient(ManagedChannel channel, StorageClientListener listener) {
        this.spanStorageStub = SpanStorageServiceGrpc.newStub(channel);
        this.listener = listener;
    }

    public void sendRequestSpan(List<RequestSpan> requestSpan) {
        StreamObserver<RequestSpan> requestSpanStreamObserver = spanStorageStub.storageRequestSpan(new StreamObserver<SendResult>() {
            @Override
            public void onNext(SendResult sendResult) {
                listener.onBatchFinished();
            }

            @Override
            public void onError(Throwable throwable) {
                listener.onError(throwable);
            }

            @Override
            public void onCompleted() {
            }
        });

        for (RequestSpan span : requestSpan) {
            requestSpanStreamObserver.onNext(span);
        }

        requestSpanStreamObserver.onCompleted();
    }

    public void sendACKSpan(List<AckSpan> ackSpan) {
        StreamObserver<AckSpan> ackSpanStreamObserver = spanStorageStub.storageACKSpan(new StreamObserver<SendResult>() {
            @Override
            public void onNext(SendResult sendResult) {
                listener.onBatchFinished();
            }

            @Override
            public void onError(Throwable throwable) {
                listener.onError(throwable);
            }

            @Override
            public void onCompleted() {
            }
        });

        for (AckSpan span : ackSpan) {
            ackSpanStreamObserver.onNext(span);
        }

        ackSpanStreamObserver.onCompleted();
    }

}
