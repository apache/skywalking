package com.ai.cloud.skywalking.analysis.viewpoint.impl;

import com.ai.cloud.skywalking.analysis.viewpoint.ViewPointFilter;
import com.ai.cloud.skywalking.protocol.Span;

public class ReplaceIPPortFilter extends ViewPointFilter {

    //ip:port regex
    private static String IP_PORT_REGEX = "^([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\\." +
            "([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\\.([0-9]|[1-9][0-9]|1([0-9][0-9])" +
            "|2([0-4][0-9]|5[0-5]))\\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5])):" +
            "([0-9]{1,4}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$";

    @Override
    public void doFilter(Span span, String viewPoint) {
        viewPoint = viewPoint.replaceAll(IP_PORT_REGEX, span.getApplicationId());

        if (getViewPointFilter() != null) {
            getViewPointFilter().doFilter(span, viewPoint);
        }
    }
}
