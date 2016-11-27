package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.network.grpc.client.TraceSearchClient;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class Client {
    private ManagedChannel                                channel;

    public Client(String ip, int address) {
        channel = ManagedChannelBuilder.forAddress(ip, address).usePlaintext(true).build();
    }

    public SpanStorageClient newSpanStorageClient(StorageClientListener listener) {
        return new SpanStorageClient(channel, listener);
    }


    public TraceSearchClient newTraceSearchClient(StorageClientListener listener){
        return new TraceSearchClient(channel, listener);
    }
}
