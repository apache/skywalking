package com.a.eye.skywalking.storage.data.spandata;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.dependencies.com.google.protobuf.InvalidProtocolBufferException;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.storage.data.exception.SpanConvertFailedException;

/**
 * Created by xin on 2016/11/12.
 */
public class SpanDataBuilder {

    private static ILog logger = LogManager.getLogger(SpanDataBuilder.class);

    public static SpanData build(RequestSpan requestSpan) {
        return new RequestSpanData(requestSpan);
    }

    public static SpanData build(AckSpan ackSpan) {
        return new AckSpanData(ackSpan);
    }

    public static AckSpan buildAckSpan(byte[] data) {
        try {
            return AckSpan.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            logger.error("Failed to convert data to ack span.", e);
            throw new SpanConvertFailedException("Failed to convert byte data to ack span", e);
        }
    }

    public static RequestSpan buildRequestSpan(byte[] data) {
        try {
            return RequestSpan.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            logger.error("Failed to convert data to request span.", e);
            throw new SpanConvertFailedException("Failed to convert byte data to request span", e);
        }
    }
}
