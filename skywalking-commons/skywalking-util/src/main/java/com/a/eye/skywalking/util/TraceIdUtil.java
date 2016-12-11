package com.a.eye.skywalking.util;

import com.a.eye.skywalking.network.grpc.TraceId;

/**
 * Created by xin on 2016/12/8.
 */
public class TraceIdUtil {
    public static String formatTraceId(TraceId traceId) {
        StringBuilder traceIdBuilder = new StringBuilder();
        for (Long segment : traceId.getSegmentsList()) {
            traceIdBuilder.append(segment).append(".");
        }

        return traceIdBuilder.substring(0, traceIdBuilder.length() - 1).toString();
    }
}
