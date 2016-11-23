import com.a.eye.skywalking.network.Client;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.storage.util.NetUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class StorageThread extends Thread {

    private SpanStorageClient client;
    private long              count;
    private CountDownLatch    countDownLatch;

    StorageThread(long count, CountDownLatch countDownLatch) {
        client = new Client("10.128.7.241", 34000).newSpanStorageClient();
        this.count = count;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        RequestSpan[] requestSpanList = new RequestSpan[10];
        AckSpan[] ackSpanList = new AckSpan[10];
        int cycle = 0;
        for (int i = 0; i < count; i++) {

            long value = System.currentTimeMillis();
            RequestSpan requestSpan = RequestSpan.newBuilder().setSpanType(1).setAddress(NetUtils.getLocalAddress().toString()).setApplicationId("1").setCallType("1").setLevelId(0)
                    .setProcessNo("19287").setStartDate(System.currentTimeMillis())
                    .setTraceId(TraceId.newBuilder().addSegments(201611).addSegments(value).addSegments(8504828).addSegments(2277).addSegments(53).addSegments(3).build())
                    .setUserId("1").setViewPointId("http://localhost:8080/wwww/test/helloWorld").build();

            AckSpan ackSpan = AckSpan.newBuilder().setLevelId(0).setCost(10).setTraceId(
                    TraceId.newBuilder().addSegments(201611).addSegments(value).addSegments(8504828).addSegments(2277).addSegments(Thread.currentThread().getId()).addSegments(3)
                            .build()).setStatusCode(0).setViewpointId("http://localhost:8080/wwww/test/helloWorld").build();

            if (cycle == 10) {
                client.sendACKSpan(ackSpanList);
                client.sendRequestSpan(requestSpanList);
                cycle = 0;
            } else {
                requestSpanList[cycle] = requestSpan;
                ackSpanList[cycle] = ackSpan;
                cycle++;
            }

            if (i % 10_000 == 0) {
                System.out.println(i + " " + value);
            }
        }

        countDownLatch.countDown();
    }
}
