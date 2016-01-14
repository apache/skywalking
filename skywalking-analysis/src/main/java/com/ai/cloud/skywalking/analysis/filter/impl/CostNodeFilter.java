package com.ai.cloud.skywalking.analysis.filter.impl;

import com.ai.cloud.skywalking.analysis.filter.ChainNodeFilter;
import com.ai.cloud.skywalking.analysis.filter.NodeChain;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.protocol.Span;

public class CostNodeFilter implements ChainNodeFilter {

    @Override
    public void doFilter(Span span, ChainNode node, CostMap costMap, NodeChain chain) {
        if (span.isReceiver()) {
            costMap.put(span.getParentLevel() + "." + span.getLevelId() + "-S", node.getCost());
        } else {
            if (costMap.exists(span.getParentLevel())) {
                costMap.put(span.getParentLevel(), costMap.get(span.getParentLevel()) + span.getCost());
            } else {
                costMap.put(span.getParentLevel(), span.getCost());
            }
            chain.doChain(span, node, costMap);
        }
    }
}
