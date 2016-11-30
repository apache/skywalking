package com.a.eye.skywalking.routing.listener;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.listener.server.SpanStorageServerListener;
import com.a.eye.skywalking.routing.disruptor.SpanDisruptor;
import com.a.eye.skywalking.routing.router.RoutingService;

/**
 * Created by xin on 2016/11/27.
 */
public class SpanStorageListenerImpl implements SpanStorageServerListener {
    @Override
    public boolean storage(RequestSpan requestSpan) {
        SpanDisruptor spanDisruptor = RoutingService.getRouter().lookup(requestSpan);
        return spanDisruptor.saveSpan(requestSpan);
    }

    @Override
    public boolean storage(AckSpan ackSpan) {
        SpanDisruptor spanDisruptor =  RoutingService.getRouter().lookup(ackSpan);
        return spanDisruptor.saveSpan(ackSpan);
    }
}
