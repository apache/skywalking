package com.a.eye.skywalking.disruptor.ack;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.lmax.disruptor.EventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wusheng on 2016/11/24.
 */
public class SendAckSpanEventHandler implements EventHandler<AckSpanHolder> {
    private static ILog logger = LogManager.getLogger(SendAckSpanEventHandler.class);
    private int           bufferSize = 100;
    private List<AckSpan> buffer     = new ArrayList<>(bufferSize);

    public SendAckSpanEventHandler() {
    }

    @Override
    public void onEvent(AckSpanHolder event, long sequence, boolean endOfBatch) throws Exception {
        buffer.add(event.getData());

        if (endOfBatch || buffer.size() == bufferSize) {
            try {
                //TODO， use GRPC to send

                HealthCollector.getCurrentHeathReading("SendAckSpanEventHandler").updateData(HeathReading.INFO, "%s messages were successful consumed .", buffer.size());
            } finally {
                buffer.clear();
            }
        }
    }
}
