package com.a.eye.skywalking.network.grpc.server;

import com.a.eye.skywalking.network.grpc.AsyncTraceSearchServiceGrpc;
import com.a.eye.skywalking.network.grpc.QueryTask;
import com.a.eye.skywalking.network.grpc.SearchResult;
import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.listener.AsyncTraceSearchListener;
import io.grpc.stub.StreamObserver;

import java.util.List;

/**
 * Created by xin on 2016/11/15.
 */
public class AsyncTraceSearchServer extends AsyncTraceSearchServiceGrpc.AsyncTraceSearchServiceImplBase {

    private AsyncTraceSearchListener searchListener;

    public AsyncTraceSearchServer(AsyncTraceSearchListener searchListener) {
        this.searchListener = searchListener;
    }

    @Override
    public StreamObserver<QueryTask> search(final StreamObserver<SearchResult> responseObserver) {
        return new StreamObserver<QueryTask>() {
            private List<Span> spans;
            private int taskId;

            @Override
            public void onNext(QueryTask value) {
                taskId = value.getTaskId();
                spans = searchListener.search(value.getTraceId());
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(SearchResult.newBuilder().addAllSpans(spans).setTaskId(taskId).build());
                responseObserver.onCompleted();
            }
        };
    }
}
