package com.a.eye.skywalking.network.grpc.provider;

import com.a.eye.skywalking.network.grpc.AsyncTraceSearchServiceGrpc;
import com.a.eye.skywalking.network.grpc.QueryTask;
import com.a.eye.skywalking.network.grpc.SearchResult;
import com.a.eye.skywalking.network.listener.AsyncTraceSearchListener;
import io.grpc.stub.StreamObserver;

/**
 * Created by xin on 2016/11/15.
 */
public class AsyncTraceSearchService extends AsyncTraceSearchServiceGrpc.AsyncTraceSearchServiceImplBase {

    public AsyncTraceSearchService(AsyncTraceSearchListener asyncTraceSearchListener) {

    }

    @Override
    public StreamObserver<QueryTask> search(StreamObserver<SearchResult> responseObserver) {
        return super.search(responseObserver);
    }
}
