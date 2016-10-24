package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.SendResult;
import com.a.eye.skywalking.network.grpc.SendingSpans;
import com.a.eye.skywalking.network.grpc.SpanSenderGrpc;
import io.grpc.stub.StreamObserver;

/**
 * Created by xin on 2016/10/25.
 */
public class GrpcSpanSender extends SpanSenderGrpc.SpanSenderImplBase {

    @Override
    public StreamObserver<SendingSpans> send(StreamObserver<SendResult> responseObserver) {
        //TODO
        return super.send(responseObserver);
    }
}
