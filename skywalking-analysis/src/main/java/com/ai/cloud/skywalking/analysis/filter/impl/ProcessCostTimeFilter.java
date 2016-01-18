package com.ai.cloud.skywalking.analysis.filter.impl;

import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;

public class ProcessCostTimeFilter extends SpanNodeProcessFilter {
    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, CostMap costMap) {
        long subNodeCost = spanEntry.getCost();
        node.setCost(spanEntry.getCost());

        if (costMap.exists(spanEntry.getParentLevelId())) {
            subNodeCost += costMap.get(spanEntry.getParentLevelId());
        }

        costMap.put(spanEntry.getParentLevelId(), subNodeCost);

        if (getNextProcessChain() != null) {
            getNextProcessChain().doFilter(spanEntry, node, costMap);
        }
    }
}
