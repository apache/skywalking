package com.a.eye.skywalking.network.grpc.server;

import com.a.eye.skywalking.network.grpc.*;
import com.a.eye.skywalking.network.listener.server.TraceSearchListener;
import io.grpc.stub.StreamObserver;

import java.util.List;

/**
 * Created by xin on 2016/11/12.
 */
public class TraceSearchServer extends TraceSearchServiceGrpc.TraceSearchServiceImplBase {

    private TraceSearchListener traceSearchListener;

    public TraceSearchServer(TraceSearchListener traceSearchListener) {
        this.traceSearchListener = traceSearchListener;
    }

    @Override
    public void search(QueryTask request, StreamObserver<SearchResult> responseObserver) {
        List<Span> spans = traceSearchListener.search(request.getTraceId());
        responseObserver.onNext(SearchResult.newBuilder().addAllSpans(spans).build());
        responseObserver.onCompleted();
    }

}
