package com.a.eye.skywalking.network.grpc.client;

import com.a.eye.skywalking.network.grpc.TraceSearchServiceGrpc;

/**
 * Created by wusheng on 2016/11/26.
 */
public class TraceSearchClient {

    private final TraceSearchServiceGrpc.TraceSearchServiceStub traceSearchServiceStub;

    public TraceSearchClient(TraceSearchServiceGrpc.TraceSearchServiceStub traceSearchServiceStub) {
        this.traceSearchServiceStub = traceSearchServiceStub;
    }

}
