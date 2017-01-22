package com.a.eye.skywalking.routing.disruptor.ack;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.routing.disruptor.AbstractRouteSpanEventHandler;
import com.a.eye.skywalking.routing.disruptor.SpanDisruptor;
import com.a.eye.skywalking.routing.router.RoutingService;

import java.util.ArrayList;
import java.util.List;

public class RouteAckSpanBufferEventHandler extends AbstractRouteSpanEventHandler<AckSpanHolder> {
    private static ILog logger = LogManager.getLogger(RouteAckSpanBufferEventHandler.class);
    private final List<AckSpan> buffer;

    public RouteAckSpanBufferEventHandler(String connectionURl) {
        super(connectionURl);
        buffer = new ArrayList<>(bufferSize);
    }

    @Override
    public String getExtraId() {
        return "AckSpanEventHandler";
    }

    @Override
    public void onEvent(AckSpanHolder event, long sequence, boolean endOfBatch) throws Exception {
        try {
            buffer.add(event.getAckSpan());

            if (stop) {
                try {
                    for (AckSpan ackSpan : buffer) {
                        SpanDisruptor spanDisruptor = RoutingService.getRouter().lookup(ackSpan);
                        spanDisruptor.saveSpan(ackSpan);
                    }
                } finally {
                    buffer.clear();
                }

                return;
            }

            wait2Finish();

            if (endOfBatch || buffer.size() == bufferSize) {
                try {
                    SpanStorageClient spanStorageClient = getStorageClient();
                    spanStorageClient.sendACKSpan(buffer);
                    HealthCollector.getCurrentHeathReading("RouteAckSpanBufferEventHandler").updateData(HeathReading.INFO, "Batch consume %s messages successfully.", buffer.size());
                } catch (Throwable e) {
                    logger.error("Ack messages consume failure.", e);
                    HealthCollector.getCurrentHeathReading("RouteAckSpanBufferEventHandler").updateData(HeathReading.ERROR, "Batch consume %s messages failure.", buffer.size());
                } finally {
                    buffer.clear();
                }
            }
        } finally {
            event.setAckSpan(null);
        }
    }
}
