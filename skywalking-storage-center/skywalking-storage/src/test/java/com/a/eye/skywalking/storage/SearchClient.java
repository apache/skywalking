package com.a.eye.skywalking.storage;

import com.a.eye.skywalking.network.dependencies.io.grpc.ManagedChannel;
import com.a.eye.skywalking.network.dependencies.io.grpc.ManagedChannelBuilder;
import com.a.eye.skywalking.network.dependencies.io.grpc.stub.StreamObserver;
import com.a.eye.skywalking.network.grpc.*;

import static com.a.eye.skywalking.network.grpc.AsyncTraceSearchServiceGrpc.newStub;


public class SearchClient {
    private static ManagedChannel channel =
            ManagedChannelBuilder.forAddress("127.0.0.1", 34000).usePlaintext(true).build();

    private static AsyncTraceSearchServiceGrpc.AsyncTraceSearchServiceStub searchServiceStub = newStub(channel);



    public static void main(String[] args) throws InterruptedException {
        StreamObserver<SearchResult> serverStreamObserver = new StreamObserver<SearchResult>() {
            @Override
            public void onNext(SearchResult searchResult) {
                System.out.println(searchResult.getSpansCount());
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        };
       StreamObserver<QueryTask> searchResult = searchServiceStub.search(serverStreamObserver);

        searchResult.onNext(QueryTask.newBuilder().setTraceId(
                TraceId.newBuilder().addSegments(201611).addSegments(1479717228982L).addSegments(8504828)
                        .addSegments(2277).addSegments(53).addSegments(3).build()).setTaskId(1).build());
        searchResult.onCompleted();

        Thread.sleep(10000);

    }
}
