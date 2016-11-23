import com.a.eye.skywalking.network.Client;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.storage.util.NetUtils;

import java.util.concurrent.CountDownLatch;

public class StorageThread extends Thread {

    private SpanStorageClient consumer;
    private long              count;
    private CountDownLatch    countDownLatch;

    StorageThread(long count, CountDownLatch countDownLatch) {
        consumer = new Client("10.128.7.241", 34000).newSpanStorageConsumer();
        this.count = count;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        for (int i = 0; i < count; i++) {
            long value = System.currentTimeMillis();
            RequestSpan requestSpan =
                    RequestSpan.newBuilder().setSpanType(1).setAddress(NetUtils.getLocalAddress().toString())
                            .setApplicationId("1").setCallType("1").setLevelId(0).setProcessNo("19287")
                            .setStartDate(System.currentTimeMillis()).setTraceId(
                            TraceId.newBuilder().addSegments(201611).addSegments(value).addSegments(8504828)
                                    .addSegments(2277).addSegments(53).addSegments(3).build()).setUserId("1")
                            .setViewPointId("http://localhost:8080/wwww/test/helloWorld").build();

            AckSpan ackSpan = AckSpan.newBuilder().setLevelId(0).setCost(10).setTraceId(
                    TraceId.newBuilder().addSegments(201611).addSegments(value).addSegments(8504828).addSegments(2277)
                            .addSegments(Thread.currentThread().getId()).addSegments(3).build()).setStatusCode(0)
                    .setViewpointId("http://localhost:8080/wwww/test/helloWorld").build();


            consumer.consumeACKSpan(ackSpan);
            consumer.consumeRequestSpan(requestSpan);

            if (i % 1_000 == 0) {
                System.out.println(i + " " + value);
            }
        }

        countDownLatch.countDown();
    }
}
