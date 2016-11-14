package com.a.eye.skywalking.network.grpc.provider;

import com.a.eye.skywalking.network.grpc.SearchResult;
import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.grpc.TraceSearchCondition;
import com.a.eye.skywalking.network.grpc.TraceSearchServiceGrpc;
import com.a.eye.skywalking.network.listener.TraceSearchListener;
import io.grpc.stub.StreamObserver;

import java.util.List;

/**
 * Created by xin on 2016/11/12.
 */
public class TraceSearchService extends TraceSearchServiceGrpc.TraceSearchServiceImplBase {

    private TraceSearchListener traceSearchListener;

    public TraceSearchService(TraceSearchListener traceSearchListener) {
        this.traceSearchListener = traceSearchListener;
    }

    @Override
    public void search(TraceSearchCondition request, StreamObserver<SearchResult> responseObserver) {
        List<Span> spans = traceSearchListener.search(request.getTraceid());
        responseObserver.onNext(SearchResult.newBuilder().addAllSpans(spans).build());
        responseObserver.onCompleted();
    }
}
