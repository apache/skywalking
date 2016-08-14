package com.a.eye.skywalking.reciever.util;

import org.mortbay.log.Log;

/**
 * Created by xin on 16-7-6.
 */
public class SpanUtil {

    public static long getTSBySpanTraceId(String traceId) {
        try {
            return Long.parseLong(traceId.split("\\.")[2]);
        } catch (Throwable t) {
            Log.warn("can't get timestamp from trace id:{}, going to use current timestamp.", traceId, t);
            return System.currentTimeMillis();
        }
    }
}
