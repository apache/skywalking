package com.a.eye.skywalking.network.listener;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;

public interface SpanStorageListener{
    boolean storage(RequestSpan requestSpan);

    boolean storage(AckSpan ackSpan);
}
