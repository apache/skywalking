package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc;
import com.a.eye.skywalking.network.grpc.consumer.SpanStorageConsumer;
import com.a.eye.skywalking.network.grpc.provider.SpanStorageService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import static com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc.newStub;

public class ConsumerProvider {
    private static ConsumerProvider                              INSTANCE;
    private        ManagedChannel                                channel;
    private        SpanStorageServiceGrpc.SpanStorageServiceStub spanStorageStub;

    private ConsumerProvider(String ip, int address) {
        channel = ManagedChannelBuilder.forAddress(ip, address).usePlaintext(true).build();
        spanStorageStub = newStub(channel);
    }

    public static ConsumerProvider INSTANCE() {
        return INSTANCE;
    }

    public SpanStorageConsumer newSpanStorageConsumer() {
        return new SpanStorageConsumer(spanStorageStub);
    }

    public static ConsumerProvider init(String ip, int address) {
        return INSTANCE = new ConsumerProvider(ip, address);
    }

}
