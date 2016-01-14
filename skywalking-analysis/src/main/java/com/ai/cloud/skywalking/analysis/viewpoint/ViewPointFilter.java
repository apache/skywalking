package com.ai.cloud.skywalking.analysis.viewpoint;

import com.ai.cloud.skywalking.protocol.Span;

public abstract class ViewPointFilter {
    private ViewPointFilter viewPointFilter;

    public abstract void doFilter(Span viewPoint, String span);

    public ViewPointFilter getViewPointFilter() {
        return viewPointFilter;
    }

    public void setViewPointFilter(ViewPointFilter viewPointFilter) {
        this.viewPointFilter = viewPointFilter;
    }

}
