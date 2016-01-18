package com.ai.cloud.skywalking.analysis.filter.impl;

import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;

public class CopyAttrFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, CostMap costMap) {
        node.setCallType(spanEntry.getCallType().toString());
        node.setStatus(spanEntry.getSpanStatus());
        node.setLevelId(spanEntry.getLevelId());
        node.setParentLevelId(spanEntry.getParentLevelId());
        node.setViewPoint(spanEntry.getViewPoint());
        node.setUserId(spanEntry.getUserId());
        node.setBusinessKey(spanEntry.getBusinessKey());

        if (getNextProcessChain() != null) {
            getNextProcessChain().doFilter(spanEntry, node, costMap);
        }
    }
}
