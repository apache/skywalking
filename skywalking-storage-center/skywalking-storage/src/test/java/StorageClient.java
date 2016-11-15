import com.a.eye.skywalking.network.dependencies.io.grpc.ManagedChannel;
import com.a.eye.skywalking.network.dependencies.io.grpc.ManagedChannelBuilder;
import com.a.eye.skywalking.network.dependencies.io.grpc.stub.StreamObserver;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.SendResult;
import com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc;

import static com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc.newStub;

public class StorageClient {
    private static ManagedChannel channel =
            ManagedChannelBuilder.forAddress("127.0.0.1", 34000).usePlaintext(true).build();

    private static SpanStorageServiceGrpc.SpanStorageServiceStub spanStorageServiceStub = newStub(channel);

    private static StreamObserver<AckSpan> ackSpanStreamObserver =
            spanStorageServiceStub.storageACKSpan(new StreamObserver<SendResult>() {
                @Override
                public void onNext(SendResult sendResult) {
                    System.out.println(sendResult.getResult());
                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                }

                @Override
                public void onCompleted() {
                    System.out.println("Success!!");
                }
            });


    private static StreamObserver<RequestSpan> requestSpanStreamObserver =
            spanStorageServiceStub.storageRequestSpan(new StreamObserver<SendResult>() {
                @Override
                public void onNext(SendResult sendResult) {
                    System.out.println(sendResult.getResult());
                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                }

                @Override
                public void onCompleted() {
                    System.out.println("Success!!");
                }
            });


    public static void main(String[] args) throws InterruptedException {
        RequestSpan requestSpan =
                RequestSpan.newBuilder().setSpanType(1).setAddress("127.0.0.1").setApplicationId("1").setCallType("1")
                        .setLevelId(0).setProcessNo("19287").setStartDate(System.currentTimeMillis())
                        .setTraceId("1.0Final.1478661327960.8504828.2277.53.3").setUserId("1")
                        .setViewPointId("http://localhost:8080/wwww/test/helloWorld").build();
        AckSpan ackSpan =
                AckSpan.newBuilder().setLevelId(0).setCost(10).setTraceId("1.0Final.1478661327960.8504828.2277.53.3")
                        .setStatusCode(0).setViewpointId("http://localhost:8080/wwww/test/helloWorld").build();

        for (int i = 0; i < 100000; i++) {
            requestSpanStreamObserver.onNext(requestSpan);
            ackSpanStreamObserver.onNext(ackSpan);
            Thread.sleep(100);
        }


        ackSpanStreamObserver.onCompleted();
        requestSpanStreamObserver.onCompleted();

        Thread.sleep(10000);

    }
}
