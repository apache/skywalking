package com.a.eye.skywalking.disruptor.request;

import com.a.eye.skywalking.client.Agent2RoutingClient;
import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.lmax.disruptor.EventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wusheng on 2016/11/24.
 */
public class SendRequestSpanEventHandler implements EventHandler<RequestSpanHolder> {
    private static ILog logger = LogManager.getLogger(SendRequestSpanEventHandler.class);
    private static final int bufferSize = 100;
    private RequestSpan[] buffer = new RequestSpan[bufferSize];
    private int bufferIdx = 0;

    public SendRequestSpanEventHandler() {
        Agent2RoutingClient.INSTANCE.setRequestSpanDataSupplier(this);
    }

    @Override
    public void onEvent(RequestSpanHolder event, long sequence, boolean endOfBatch) throws Exception {
        if (buffer[bufferIdx] != null) {
            return;
        }

        buffer[bufferIdx] = event.getData();
        bufferIdx++;

        if (bufferIdx == buffer.length) {
            bufferIdx = 0;
        }

        if (endOfBatch) {
            HealthCollector.getCurrentHeathReading("SendRequestSpanEventHandler").updateData(HeathReading.INFO, "Request Span messages were successful consumed .");
        }
    }

    public List<RequestSpan> getBufferData() {
        List<RequestSpan> data = new ArrayList<RequestSpan>(bufferSize);
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] != null) {
                data.add(buffer[i]);
                buffer[i] = null;
            }
        }
        return data;
    }
}
