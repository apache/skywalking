package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc;
import com.a.eye.skywalking.network.grpc.TraceSearchServiceGrpc;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.network.grpc.client.TraceSearchClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class Client {
    private ManagedChannel                                channel;
    private SpanStorageServiceGrpc.SpanStorageServiceStub spanStorageStub;
    private TraceSearchServiceGrpc.TraceSearchServiceStub traceSearchServiceStub;

    public Client(String ip, int address) {
        channel = ManagedChannelBuilder.forAddress(ip, address).usePlaintext(true).build();
        spanStorageStub = SpanStorageServiceGrpc.newStub(channel);
        traceSearchServiceStub = TraceSearchServiceGrpc.newStub(channel);
    }


    public SpanStorageClient newSpanStorageClient() {
        return new SpanStorageClient(spanStorageStub);
    }


    public TraceSearchClient newTraceSearchClient(){
        return new TraceSearchClient(traceSearchServiceStub);
    }
}
