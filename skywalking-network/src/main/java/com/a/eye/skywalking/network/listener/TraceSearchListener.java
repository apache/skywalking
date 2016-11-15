package com.a.eye.skywalking.network.listener;

import com.a.eye.skywalking.network.grpc.Span;

import java.util.List;

public interface TraceSearchListener{
    List<Span> search(String traceId);
}
