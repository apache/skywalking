package com.a.eye.skywalking.network.grpc.client;

import com.a.eye.skywalking.network.grpc.AsyncTraceSearchServiceGrpc;
import com.a.eye.skywalking.network.grpc.QueryTask;
import com.a.eye.skywalking.network.grpc.SearchResult;
import com.a.eye.skywalking.network.grpc.TraceSearchServiceGrpc;
import com.a.eye.skywalking.network.listener.client.SearchClientListener;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

/**
 * Created by wusheng on 2016/11/26.
 */
public class TraceSearchClient {
    private final AsyncTraceSearchServiceGrpc.AsyncTraceSearchServiceStub traceSearchServiceStub;
    private final TraceSearchServiceGrpc.TraceSearchServiceBlockingStub   traceSearchServiceBlockingStub;

    public TraceSearchClient(ManagedChannel channel) {
        this.traceSearchServiceStub = AsyncTraceSearchServiceGrpc.newStub(channel);
        this.traceSearchServiceBlockingStub = TraceSearchServiceGrpc.newBlockingStub(channel);
    }

    public void search(QueryTask queryTask, final SearchClientListener listener){
        StreamObserver<SearchResult> serverStreamObserver = new StreamObserver<SearchResult>() {
            @Override
            public void onNext(SearchResult searchResult) {
                listener.onReturn(searchResult);
            }

            @Override
            public void onError(Throwable throwable) {
                listener.onError(throwable);
            }

            @Override
            public void onCompleted() {
                listener.onFinished();
            }
        };

        StreamObserver<QueryTask> searchResult = traceSearchServiceStub.search(serverStreamObserver);
        searchResult.onNext(queryTask);
        searchResult.onCompleted();
    }

    public SearchResult search(QueryTask queryTask){
        return traceSearchServiceBlockingStub.search(queryTask);
    }
}
