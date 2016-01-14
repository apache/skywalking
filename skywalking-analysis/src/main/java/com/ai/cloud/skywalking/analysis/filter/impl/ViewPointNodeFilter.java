package com.ai.cloud.skywalking.analysis.filter.impl;

import com.ai.cloud.skywalking.analysis.config.ViewPointFilterFactory;
import com.ai.cloud.skywalking.analysis.filter.ChainNodeFilter;
import com.ai.cloud.skywalking.analysis.filter.NodeChain;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.viewpoint.ViewPointFilter;
import com.ai.cloud.skywalking.protocol.Span;

public class ViewPointNodeFilter implements ChainNodeFilter {
    @Override
    public void doFilter(Span span, ChainNode node, CostMap costMap, NodeChain chain) {
        ViewPointFilter viewPointFilter = ViewPointFilterFactory.getFilter(span.getSpanType());
        String viewPoint = span.getViewPointId();
        viewPointFilter.doFilter(span, viewPoint);
        node.setViewPoint(viewPoint);
        chain.doChain(span, node, costMap);
    }
}
