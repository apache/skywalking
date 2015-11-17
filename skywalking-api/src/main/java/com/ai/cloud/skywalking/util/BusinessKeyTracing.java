package com.ai.cloud.skywalking.util;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;

public final class BusinessKeyTracing {

    private static final char spiltChar = '^';


    private BusinessKeyTracing() {
        // Non
    }

    public static void trace(String businessKey) {

        if (!AuthDesc.isAuth())
            return;

        Span spanData = Context.getLastSpan();
        if (spanData != null) {
            return;
        }
        spanData.setBusinessKey(businessKey.replace('-', spiltChar).substring(0, Config.BusinessKey.MAX_LENGTH));
    }
}
