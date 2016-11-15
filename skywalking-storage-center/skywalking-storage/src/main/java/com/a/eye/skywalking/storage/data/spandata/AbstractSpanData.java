package com.a.eye.skywalking.storage.data.spandata;

import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.storage.data.exception.IllegalTraceIdException;

/**
 * Created by xin on 2016/11/12.
 */
public abstract class AbstractSpanData implements SpanData{

    protected String buildLevelId(String parentLevelId, int levelId) {
        return (parentLevelId == null || parentLevelId.length() == 0) ? levelId + "" : parentLevelId + "." + levelId;
    }

    protected Long[] traceIdToArrays(TraceId traceId) {
        if (traceId.getSegmentsCount() != 6){
            throw new IllegalTraceIdException("The length of traceId must equals five.");
        }
        return traceId.getSegmentsList().toArray(new Long[traceId.getSegmentsCount()]);
    }
}
