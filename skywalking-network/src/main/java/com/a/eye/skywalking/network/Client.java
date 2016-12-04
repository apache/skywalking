package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.network.grpc.client.TraceSearchClient;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class Client {
    private ManagedChannel channel;

    public Client(String ip, int port) {
        channel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext(true).build();
    }

    public SpanStorageClient newSpanStorageClient(StorageClientListener listener) {
        return new SpanStorageClient(channel, listener);
    }

    public TraceSearchClient newTraceSearchClient() {
        return new TraceSearchClient(channel);
    }

    public void shutdown() {
        channel.shutdownNow();
    }

    public boolean isShutdown() {
        return channel.isShutdown() || channel.isTerminated();
    }
}
