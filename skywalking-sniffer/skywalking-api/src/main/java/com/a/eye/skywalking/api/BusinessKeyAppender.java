package com.a.eye.skywalking.api;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.model.Span;

public final class BusinessKeyAppender {

    private BusinessKeyAppender() {
        // Non
    }

    /**
     * 在当前埋点上下文中设置业务级信息
     */
    public static void setBusinessKey2Trace(String businessKey) {

        Span spanData = CurrentThreadSpanStack.peek();
        if (spanData == null) {
            return;
        }
        if (businessKey.length() <= Config.BuriedPoint.BUSINESSKEY_MAX_LENGTH) {
            spanData.setBusinessKey(businessKey);
            return;
        }
        spanData.setBusinessKey(businessKey.substring(0, Config.BuriedPoint.BUSINESSKEY_MAX_LENGTH));
    }


}
