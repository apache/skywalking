package com.a.eye.skywalking.network.grpc.client;

import com.a.eye.skywalking.network.exception.ConsumeSpanDataFailedException;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.SendResult;
import com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;

public class SpanStorageClient {

    private final SpanStorageServiceGrpc.SpanStorageServiceStub spanStorageStub;

    public SpanStorageClient(SpanStorageServiceGrpc.SpanStorageServiceStub spanStorageStub) {
        this.spanStorageStub = spanStorageStub;
    }

    public void sendRequestSpan(RequestSpan... requestSpan) {
        StreamObserver<RequestSpan> requestSpanStreamObserver =
                spanStorageStub.storageRequestSpan(new StreamObserver<SendResult>() {
                    @Override
                    public void onNext(SendResult sendResult) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throwable.printStackTrace();
                    }

                    @Override
                    public void onCompleted() {

                    }
                });

        for (RequestSpan span : requestSpan) {
            requestSpanStreamObserver.onNext(span);
        }
        while (!((CallStreamObserver<RequestSpan>) requestSpanStreamObserver).isReady()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new ConsumeSpanDataFailedException(e);
            }
        }

        requestSpanStreamObserver.onCompleted();
    }

    public void sendACKSpan(AckSpan... ackSpan) {
        StreamObserver<AckSpan> ackSpanStreamObserver =
                spanStorageStub.storageACKSpan(new StreamObserver<SendResult>() {
                    @Override
                    public void onNext(SendResult sendResult) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throwable.printStackTrace();
                    }

                    @Override
                    public void onCompleted() {

                    }
                });

        for (AckSpan span : ackSpan) {
            ackSpanStreamObserver.onNext(span);
        }
        while (!((CallStreamObserver<AckSpan>) ackSpanStreamObserver).isReady()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new ConsumeSpanDataFailedException(e);
            }
        }

        ackSpanStreamObserver.onCompleted();
    }

}
