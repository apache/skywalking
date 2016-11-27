package com.a.eye.skywalking.network.grpc.client;

import com.a.eye.skywalking.network.grpc.TraceSearchServiceGrpc;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;
import io.grpc.ManagedChannel;

/**
 * Created by wusheng on 2016/11/26.
 */
public class TraceSearchClient {

    private final TraceSearchServiceGrpc.TraceSearchServiceStub traceSearchServiceStub;

    public TraceSearchClient(ManagedChannel channel, StorageClientListener listener) {
        this.traceSearchServiceStub = TraceSearchServiceGrpc.newStub(channel);
    }

}
