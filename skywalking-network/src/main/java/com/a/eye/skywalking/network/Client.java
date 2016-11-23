package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import static com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc.newStub;

public class Client {
    private ManagedChannel                                channel;
    private SpanStorageServiceGrpc.SpanStorageServiceStub spanStorageStub;

    public Client(String ip, int address) {
        channel = ManagedChannelBuilder.forAddress(ip, address).usePlaintext(true).build();
        spanStorageStub = newStub(channel);
    }


    public SpanStorageClient newSpanStorageConsumer() {
        return new SpanStorageClient(spanStorageStub);
    }


}
