package com.a.eye.skywalking.network.grpc.services;

import com.a.eye.skywalking.network.grpc.SearchResult;
import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.grpc.TraceSearchCondition;
import com.a.eye.skywalking.network.grpc.TraceSearchServiceGrpc;
import com.a.eye.skywalking.network.listener.TraceSearchNotifier;
import io.grpc.stub.StreamObserver;

import java.util.List;

/**
 * Created by xin on 2016/11/12.
 */
public class TraceSearchService extends TraceSearchServiceGrpc.TraceSearchServiceImplBase {

    private TraceSearchNotifier traceSearchNotifier;

    public TraceSearchService(TraceSearchNotifier traceSearchNotifier) {
        this.traceSearchNotifier = traceSearchNotifier;
    }

    @Override
    public void search(TraceSearchCondition request, StreamObserver<SearchResult> responseObserver) {
        List<Span> spans = traceSearchNotifier.search(request.getTraceid());
        responseObserver.onNext(SearchResult.newBuilder().addAllSpans(spans).build());
        responseObserver.onCompleted();
    }
}
