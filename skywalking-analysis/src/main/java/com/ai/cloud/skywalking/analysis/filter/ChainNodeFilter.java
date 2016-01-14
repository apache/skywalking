package com.ai.cloud.skywalking.analysis.filter;

import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.protocol.Span;

public interface ChainNodeFilter {
    void doFilter(Span span, ChainNode node, CostMap costMap, NodeChain chain);
}
