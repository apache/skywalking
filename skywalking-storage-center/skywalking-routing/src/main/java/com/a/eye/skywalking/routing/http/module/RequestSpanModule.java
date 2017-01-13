package com.a.eye.skywalking.routing.http.module;

import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.util.StringUtil;
import com.a.eye.skywalking.util.TraceIdUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * request span module
 * <p>
 * All fields in this class will be initialized by {@link com.google.gson.Gson#fromJson(String, Class)},
 * ignore the un-assign values warning.
 */
public class RequestSpanModule {
    private String traceId;
    private String parentLevelId = "";
    private int                 levelId;
    private long                startTime;
    private int                 routeKey;
    private Map<String, String> tags;

    public RequestSpan convertToGRPCModule() {
        if (illegalRequestSpan()) {
            return null;
        }
        return RequestSpan.newBuilder().putAllTags(tags).setLevelId(levelId).setParentLevel(parentLevelId)
                .setRouteKey(routeKey).setStartDate(startTime).setTraceId(TraceIdUtil.toTraceId(traceId)).build();

    }

    private boolean illegalRequestSpan() {
        if (StringUtil.isEmpty(traceId)) {
            return true;
        }
        if (tags.isEmpty()) {
            return true;
        }
        return false;
    }
}
