package com.a.eye.skywalking.disruptor.request;

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
    private static       ILog              logger     = LogManager.getLogger(SendRequestSpanEventHandler.class);
    private static final int               bufferSize = 100;
    private              List<RequestSpan> buffer     = new ArrayList<>(bufferSize);

    public SendRequestSpanEventHandler() {
    }

    @Override
    public void onEvent(RequestSpanHolder event, long sequence, boolean endOfBatch) throws Exception {
        buffer.add(event.getData());

        if (endOfBatch || buffer.size() == bufferSize) {
            try {


                HealthCollector.getCurrentHeathReading("SendRequestSpanEventHandler").updateData(HeathReading.INFO, "%s messages were successful consumed .", buffer.size());
            } finally {
                buffer.clear();
            }
        }
    }
}
