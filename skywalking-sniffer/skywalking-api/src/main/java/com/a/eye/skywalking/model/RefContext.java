package com.a.eye.skywalking.model;

import com.a.eye.skywalking.network.grpc.TraceId;

/**
 * Created by xin on 2017/1/17.
 */
public class RefContext{
    private TraceId traceId;
    private String parentLevelId;

    public RefContext(String contentDataStr) {
        ContextData contextData = new ContextData(contentDataStr);
        this.traceId = contextData.getTraceId();
        this.parentLevelId = contextData.getParentLevel();
    }

    public TraceId getTraceId() {
        return traceId;
    }

    public String getParentLevelId() {
        return parentLevelId;
    }
}
