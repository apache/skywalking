package com.a.eye.skywalking.routing.disruptor.request;


import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.routing.disruptor.SpanDisruptor;
import com.a.eye.skywalking.routing.router.RoutingService;
import com.a.eye.skywalking.routing.disruptor.AbstractRouteSpanEventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/11/27.
 */
public class RouteSendRequestSpanEventHandler extends AbstractRouteSpanEventHandler<RequestSpanHolder> {
    private static ILog logger = LogManager.getLogger(RouteSendRequestSpanEventHandler.class);
    private final List<RequestSpan> buffer;

    public RouteSendRequestSpanEventHandler(String connectionURl) {
        super(connectionURl);
        buffer = new ArrayList<>(bufferSize);
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
                for (RequestSpan requestSpan : buffer) {
                    SpanDisruptor spanDisruptor = RoutingService.getRouter().lookup(requestSpan);
                    spanDisruptor.saveSpan(requestSpan);
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
                spanStorageClient.sendRequestSpan(buffer);
                HealthCollector.getCurrentHeathReading("RouteSendRequestSpanEventHandler").updateData(HeathReading.INFO, "Batch consume %s messages successfully.", buffer.size());
            } catch (Throwable e) {
                logger.error("RequestSpan messages consume failure.", e);
                HealthCollector.getCurrentHeathReading("RouteSendRequestSpanEventHandler").updateData(HeathReading.ERROR, "Batch consume %s messages failure.", buffer.size());
            } finally {
                buffer.clear();
            }
        }
    }
}
