package com.ai.cloud.skywalking.util;

import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;

public final class BusinessKeyTracing {

    private static final char spiltChar='^';


    private BusinessKeyTracing() {
        // Non
    }

    public static void trace(String businessKey) {
        //
        Span spanData = Context.getLastSpan();
        spanData.setBusinessKey(businessKey.replace('-',spiltChar));
    }
}
