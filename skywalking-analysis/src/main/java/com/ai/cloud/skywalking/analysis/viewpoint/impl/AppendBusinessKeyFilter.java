package com.ai.cloud.skywalking.analysis.viewpoint.impl;

import com.ai.cloud.skywalking.analysis.viewpoint.ViewPointFilter;
import com.ai.cloud.skywalking.protocol.Span;

public class AppendBusinessKeyFilter extends ViewPointFilter {
    @Override
    public void doFilter(Span span, String viewPoint) {
        viewPoint += span.getBusinessKey();

        if (getViewPointFilter() != null) {
            getViewPointFilter().doFilter(span, viewPoint);
        }
    }
}
