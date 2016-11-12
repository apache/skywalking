package com.a.eye.skywalking.network.listener;

import com.a.eye.skywalking.network.grpc.Span;

import java.util.List;

/**
 * Created by xin on 2016/11/12.
 */
public interface TraceSearchNotifier {
    List<Span> search(String traceId);
}
