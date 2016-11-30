package com.a.eye.skywalking.routing.disruptor.request;


import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.routing.disruptor.SpanDisruptor;
import com.a.eye.skywalking.routing.router.RoutingService;
import com.a.eye.skywalking.routing.disruptor.AbstractSpanEventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/11/27.
 */
public class SendRequestSpanEventHandler extends AbstractSpanEventHandler<RequestSpanHolder> {
    private List<RequestSpan> buffer = new ArrayList<>(bufferSize);

    public SendRequestSpanEventHandler(String connectionURl) {
        super(connectionURl);
    }

    @Override
    public String getExtraId() {
        return "RequestSpanEventHandler";
    }

    @Override
    public void onEvent(RequestSpanHolder event, long sequence, boolean endOfBatch) throws Exception {
        buffer.add(event.getRequestSpan());

        if (stop) {
            try {
                for (RequestSpan ackSpan : buffer) {
                    SpanDisruptor spanDisruptor = RoutingService.getRouter().lookup(ackSpan);
                    spanDisruptor.saveSpan(ackSpan);
                }
            } finally {
                buffer.clear();
            }

            return;
        }

        while (!previousSendResult) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
            }
        }

        if (endOfBatch || buffer.size() == bufferSize) {
            SpanStorageClient spanStorageClient = getStorageClient();
            spanStorageClient.sendRequestSpan(buffer);
            buffer.clear();
        }
    }
}
