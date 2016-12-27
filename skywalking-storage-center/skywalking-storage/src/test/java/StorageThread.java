import com.a.eye.skywalking.network.Client;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;
import com.a.eye.skywalking.network.model.Tag;
import com.a.eye.skywalking.registry.assist.NetUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class StorageThread extends Thread {

    private SpanStorageClient client;
    private long count;
    private CountDownLatch countDownLatch;
    private MyStorageClientListener listener;
    private int index;


    StorageThread(long count, CountDownLatch countDownLatch, int index) {
        listener = new MyStorageClientListener();
        client = new Client("10.128.6.79", 23000).newSpanStorageClient(listener);
        this.count = count;
        this.countDownLatch = countDownLatch;
        this.index = index;
    }

    @Override
    public void run() {
        List<RequestSpan> requestSpanList = new ArrayList<RequestSpan>();
        List<AckSpan> ackSpanList = new ArrayList<AckSpan>();
        int cycle = 0;
        for (int i = 0; i < count; i++) {

            long value = System.currentTimeMillis();
            RequestSpan requestSpan = RequestSpan.newBuilder().putTags(Tag.SPAN_TYPE.toString(), "1")
                    .putTags(Tag.ADDRESS.toString(), NetUtils.getLocalAddress().toString())
                    .putTags(Tag.APPLICATION_CODE.toString(), "1")
                    .putTags(Tag.CALL_TYPE.toString(), "1").setLevelId(0)
                    .putTags(Tag.PROCESS_NO.toString(), "19287").setStartDate(System.currentTimeMillis())
                    .setTraceId(TraceId.newBuilder().addSegments(201611).addSegments(value).addSegments(8504828).addSegments(2277).addSegments(53).addSegments(3).build())
                    .putTags(Tag.USER_NAME.toString(), "1").putTags(Tag.VIEW_POINT.toString(), "http://localhost:8080/wwww/test/helloWorld").setRouteKey(i).build();

            AckSpan ackSpan = AckSpan.newBuilder().setLevelId(0).setCost(10).setTraceId(
                    TraceId.newBuilder().addSegments(201611).addSegments(value).addSegments(8504828).addSegments(2277).addSegments(53).addSegments(3)
                            .build()).putTags(Tag.STATUS.toString(), "0").putTags(Tag.VIEW_POINT.toString(), "http://localhost:8080/wwww/test/helloWorld").setRouteKey(i).build();
            requestSpanList.add(requestSpan);
            ackSpanList.add(ackSpan);
            cycle++;

            if (cycle == 10) {
                client.sendACKSpan(ackSpanList);
                client.sendRequestSpan(requestSpanList);
                cycle = 0;

                while (!listener.isCompleted) {
                    try {
                        Thread.sleep(1L);
                    } catch (InterruptedException e) {
                    }
                }
                listener.begin();
                ackSpanList.clear();
                requestSpanList.clear();
            }

            if (i % 10_000 == 0) {
                System.out.println("index-" + index + " num=" + i + " " + value);
            }
        }

        countDownLatch.countDown();
    }

    public class MyStorageClientListener implements StorageClientListener {
        volatile boolean isCompleted = false;

        @Override
        public void onError(Throwable throwable) {
            isCompleted = true;
        }

        @Override
        public void onBatchFinished() {
            isCompleted = true;
        }

        public void begin() {
            isCompleted = false;
        }
    }
}
