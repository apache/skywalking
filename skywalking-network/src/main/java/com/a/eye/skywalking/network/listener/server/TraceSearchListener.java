package com.a.eye.skywalking.network.listener.server;

import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.grpc.TraceId;

import java.util.List;

public interface TraceSearchListener{
    List<Span> search(TraceId traceId);
}
