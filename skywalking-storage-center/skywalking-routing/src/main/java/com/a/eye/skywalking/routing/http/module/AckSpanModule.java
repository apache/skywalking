package com.a.eye.skywalking.routing.http.module;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.util.StringUtil;
import com.a.eye.skywalking.util.TraceIdUtil;

import java.util.Map;

import static com.a.eye.skywalking.util.TraceIdUtil.isIllegalTraceId;

/**
 * Ack span module
 * <p>
 * All fields in this class will be initialized by {@link com.google.gson.Gson#fromJson(String, Class)}, ignore the un-assign values warning.
 */
public class AckSpanModule {
    private String traceId;
    private String parentLevelId;
    private int    levelId;
    private long   cost;
    private int    routeKey;

    private Map<String, String> tags;

    public AckSpan convertToGRPCModule() {
        if (illegalAckSpan()) {
            return null;
        }

        return AckSpan.newBuilder().putAllTags(tags).setLevelId(levelId).setParentLevel(parentLevelId)
                .setRouteKey(routeKey).setCost(cost).setTraceId(TraceIdUtil.toTraceId(traceId)).build();
    }

    private boolean illegalAckSpan() {
        if (isIllegalTraceId(traceId)) {
            return true;
        }

        if (tags.isEmpty()) {
            return true;
        }
        return false;
    }
}
