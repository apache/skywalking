package com.ai.cloud.skywalking.analysis.filter.impl;

import com.ai.cloud.skywalking.analysis.filter.ChainNodeFilter;
import com.ai.cloud.skywalking.analysis.filter.NodeChain;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.protocol.Span;

public class CopyAttrNodeFilter implements ChainNodeFilter {

    @Override
    public void doFilter(Span span, ChainNode node, CostMap costMap, NodeChain chain) {
        node.setCost(span.getCost());
        node.setLevelId(span.getLevelId());
        node.setParentLevelId(span.getParentLevel());
        node.setCallType(span.getCallType());

        if (span.getExceptionStack() == null || span.getExceptionStack().length() == 0) {
            node.setStatus(ChainNode.NodeStatus.NORMAL);
        } else {
            node.setStatus(ChainNode.NodeStatus.ABNORMAL);
        }

        chain.doChain(span, node, costMap);
    }
}
