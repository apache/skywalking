package com.ai.cloud.skywalking.api;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.protocol.Span;

public class Tracing {
	/**
	 * 获取当前上下文中的TraceId
	 * @return
	 */
	public static String getTraceId(){
		if (!AuthDesc.isAuth())
            return "";

        Span spanData = Context.getLastSpan();
        if (spanData == null) {
            return "";
        }
        
        return spanData.getTraceId();
	}
}
