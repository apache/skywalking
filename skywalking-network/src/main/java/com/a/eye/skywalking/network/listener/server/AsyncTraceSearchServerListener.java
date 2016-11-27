package com.a.eye.skywalking.network.listener.server;

import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.grpc.TraceId;

import java.util.List;

/**
 * Created by xin on 2016/11/15.
 */
public interface AsyncTraceSearchServerListener {
    List<Span> search(TraceId traceId);
}
