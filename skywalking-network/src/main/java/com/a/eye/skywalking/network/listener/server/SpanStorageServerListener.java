package com.a.eye.skywalking.network.listener.server;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;

public interface SpanStorageServerListener {
    boolean storage(RequestSpan requestSpan);

    boolean storage(AckSpan ackSpan);
}
