package com.a.eye.skywalking.network.grpc.server;

import com.a.eye.skywalking.network.grpc.AsyncTraceSearchServiceGrpc;
import com.a.eye.skywalking.network.grpc.QueryTask;
import com.a.eye.skywalking.network.grpc.SearchResult;
import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.listener.server.TraceSearchListener;
import io.grpc.stub.StreamObserver;

import java.util.List;

/**
 * Created by xin on 2016/11/15.
 */
public class AsyncTraceSearchServer extends AsyncTraceSearchServiceGrpc.AsyncTraceSearchServiceImplBase {

    private TraceSearchListener searchListener;

    public AsyncTraceSearchServer(TraceSearchListener searchListener) {
        this.searchListener = searchListener;
    }

    @Override
    public StreamObserver<QueryTask> search(final StreamObserver<SearchResult> responseObserver) {
        return new StreamObserver<QueryTask>() {
            private List<Span> spans;

            @Override
            public void onNext(QueryTask value) {
                spans = searchListener.search(value.getTraceId());
            }

            @Override
            public void onError(Throwable t) {
                SearchResult.Builder builder = SearchResult.newBuilder();
                responseObserver.onNext(builder.build());
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                SearchResult.Builder builder = SearchResult.newBuilder();
                if(spans != null) {
                    builder = builder.addAllSpans(spans);
                }
                responseObserver.onNext(builder.build());
                responseObserver.onCompleted();
            }
        };
    }
}
