package com.ai.cloud.skywalking.api;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;

public final class BusinessKeyAppender {
    private static final char spiltChar = '^';

    private BusinessKeyAppender() {
        // Non
    }

    /**
     * 在当前埋点上下文中设置业务级信息
     * 
     * @param businessKey
     */
    public static void trace(String businessKey) {
        if (!AuthDesc.isAuth())
            return;

        Span spanData = Context.getLastSpan();
        if (spanData == null) {
            return;
        }
        spanData.setBusinessKey(businessKey.replace('-', spiltChar).substring(0, Config.BusinessKey.MAX_LENGTH));
    }
}
