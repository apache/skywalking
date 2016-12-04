package com.a.eye.skywalking.disruptor.ack;

import com.a.eye.skywalking.client.Agent2RoutingClient;
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
    private int bufferSize = 100;
    private AckSpan[] buffer = new AckSpan[bufferSize];
    private int bufferIdx = 0;

    public SendAckSpanEventHandler() {
        Agent2RoutingClient.INSTANCE.setAckSpanDataSupplier(this);
    }

    @Override
    public void onEvent(AckSpanHolder event, long sequence, boolean endOfBatch) throws Exception {
        if (buffer[bufferIdx] != null) {
            return;
        }

        buffer[bufferIdx] = event.getData();
        bufferIdx++;

        if (bufferIdx == buffer.length) {
            bufferIdx = 0;
        }

        if (endOfBatch) {
            HealthCollector.getCurrentHeathReading("SendAckSpanEventHandler").updateData(HeathReading.INFO, "AckSpan messages were successful consumed .");
        }
    }

    public List<AckSpan> getBufferData() {
        List<AckSpan> data = new ArrayList<AckSpan>(bufferSize);
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] != null) {
                data.add(buffer[i]);
                buffer[i] = null;
            }
        }
        return data;
    }
}
