package com.a.eye.skywalking.routing.disruptor;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.routing.disruptor.ack.AckSpanDisruptor;
import com.a.eye.skywalking.routing.disruptor.request.RequestSpanDisruptor;

public class SpanDisruptor {

    private AckSpanDisruptor ackSpanDisruptor;
    private RequestSpanDisruptor requestSpanDisruptor;
    private String connectionURL;

    SpanDisruptor() {
    }

    public SpanDisruptor(String connectionURL) {
        requestSpanDisruptor = new RequestSpanDisruptor(connectionURL);
        ackSpanDisruptor = new AckSpanDisruptor(connectionURL);
        this.connectionURL  = connectionURL;
    }

    public boolean saveSpan(RequestSpan requestSpan) {
        return requestSpanDisruptor.saveRequestSpan(requestSpan);
    }

    public boolean saveSpan(AckSpan ackSpan) {
        return ackSpanDisruptor.saveAckSpan(ackSpan);
    }

    public String getConnectionURL() {
        return connectionURL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SpanDisruptor that = (SpanDisruptor) o;

        return connectionURL != null ? connectionURL.equals(that.connectionURL) : that.connectionURL == null;
    }

    @Override
    public int hashCode() {
        return connectionURL != null ? connectionURL.hashCode() : 0;
    }

    public void shutdown() {
        ackSpanDisruptor.shutdown();
        ackSpanDisruptor.shutdown();
    }
}
