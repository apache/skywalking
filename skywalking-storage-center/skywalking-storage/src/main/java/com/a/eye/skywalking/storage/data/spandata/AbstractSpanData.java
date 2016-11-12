package com.a.eye.skywalking.storage.data.spandata;

/**
 * Created by xin on 2016/11/12.
 */
public abstract class AbstractSpanData implements SpanData{

    protected String buildLevelId(String parentLevelId, int levelId) {
        return (parentLevelId == null || parentLevelId.length() == 0) ? levelId + "" : parentLevelId + "." + levelId;
    }

    protected static long buildTraceStartTime(String traceId) {
        String[] traceIdSegment = traceId.split("\\.");
        return Long.parseLong(traceIdSegment[traceIdSegment.length - 5]);
    }
}
