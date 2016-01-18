package com.ai.cloud.skywalking.analysis.filter.impl;

import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;

public class AppendBusinessKeyFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, CostMap costMap) {
        node.setViewPoint(node.getViewPoint() + spanEntry.getBusinessKey());

        if (getNextProcessChain() != null) {
            getNextProcessChain().doFilter(spanEntry, node, costMap);
        }
    }
}
