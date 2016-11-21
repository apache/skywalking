import com.a.eye.skywalking.network.dependencies.io.grpc.ManagedChannel;
import com.a.eye.skywalking.network.dependencies.io.grpc.ManagedChannelBuilder;
import com.a.eye.skywalking.network.dependencies.io.grpc.stub.ClientCallStreamObserver;
import com.a.eye.skywalking.network.dependencies.io.grpc.stub.ServerCallStreamObserver;
import com.a.eye.skywalking.network.dependencies.io.grpc.stub.StreamObserver;
import com.a.eye.skywalking.network.grpc.*;

import static com.a.eye.skywalking.network.grpc.SpanStorageServiceGrpc.newStub;

public class StorageClient {

    private static ManagedChannel channel =
            ManagedChannelBuilder.forAddress("127.0.0.1", 34000).usePlaintext(true).build();

    private static SpanStorageServiceGrpc.SpanStorageServiceStub spanStorageServiceStub = newStub(channel);

    private static long endTime1 = 0;

    private static long endTime2 = 0;


    public static void main(String[] args) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1; i++) {
            long value = System.currentTimeMillis();
            RequestSpan requestSpan =
                    RequestSpan.newBuilder().setSpanType(1).setAddress("127.0.0.1").setApplicationId("1")
                            .setCallType("1").setLevelId(0).setProcessNo("19287")
                            .setStartDate(System.currentTimeMillis()).setTraceId(
                            TraceId.newBuilder().addSegments(201611).addSegments(value)
                                    .addSegments(8504828).addSegments(2277).addSegments(53).addSegments(3).build())
                            .setUserId("1").setViewPointId("http://localhost:8080/wwww/test/helloWorld").build();

            AckSpan ackSpan = AckSpan.newBuilder().setLevelId(0).setCost(10).setTraceId(
                    TraceId.newBuilder().addSegments(201611).addSegments(value).addSegments(8504828).addSegments(2277)
                            .addSegments(53).addSegments(3).build()).setStatusCode(0)
                    .setViewpointId("http://localhost:8080/wwww/test/helloWorld").build();

            StreamObserver<AckSpan> ackSpanStreamObserver =
                    spanStorageServiceStub.storageACKSpan(new StreamObserver<SendResult>() {
                        @Override
                        public void onNext(SendResult sendResult) {
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            throwable.printStackTrace();
                        }

                        @Override
                        public void onCompleted() {
                            endTime1 = System.currentTimeMillis();
                        }
                    });
            StreamObserver<RequestSpan> requestSpanStreamObserver =
                    spanStorageServiceStub.storageRequestSpan(new StreamObserver<SendResult>() {
                        @Override
                        public void onNext(SendResult sendResult) {

                        }

                        @Override
                        public void onError(Throwable throwable) {
                            throwable.printStackTrace();
                        }

                        @Override
                        public void onCompleted() {
                            endTime2 = System.currentTimeMillis();
                        }
                    });
            for (int j = 0; j < 1; j++) {
                requestSpanStreamObserver.onNext(requestSpan);
                ackSpanStreamObserver.onNext(ackSpan);

            }

            ClientCallStreamObserver<RequestSpan> newRequestSpanStreamObserver =
                    (ClientCallStreamObserver<RequestSpan>) requestSpanStreamObserver;

            while (!newRequestSpanStreamObserver.isReady()) {
                Thread.sleep(1);
            }

            ackSpanStreamObserver.onCompleted();
            requestSpanStreamObserver.onCompleted();


            if (i % 1_000 == 0) {
                System.out.println(i);
            }

        }

        Thread.sleep(1000L);

        System.out.println("save execute in " + (endTime1 - startTime) + "ms");
        System.out.println("save execute2 in " + (endTime2 - startTime) + "ms");


        Thread.sleep(10000);

    }
}
