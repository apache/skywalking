package com.ai.cloud.skywalking.api;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.protocol.Span;

public final class BusinessKeyAppender {

    private BusinessKeyAppender() {
        // Non
    }

    /**
     * 在当前埋点上下文中设置业务级信息
     *
     * @param businessKey
     */
    public static void setBusinessKey2Trace(String businessKey) {
        if (!AuthDesc.isAuth())
            return;

        Span spanData = Context.getLastSpan();
        if (spanData == null) {
            return;
        }
        if (businessKey.length() <= Config.BusinessKey.MAX_LENGTH) {
            spanData.setBusinessKey(businessKey);
            return;
        }
        spanData.setBusinessKey(businessKey.substring(0, Config.BusinessKey.MAX_LENGTH));
    }
}
