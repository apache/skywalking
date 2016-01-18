package com.ai.cloud.skywalking.analysis.filter.impl;

import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;
import com.ai.cloud.skywalking.analysis.util.TokenGenerator;

public class TokenGenerateFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, CostMap costMap) {

        String nodeToken = TokenGenerator.generate(node.getParentLevelId() + "." + node.getLevelId() +
                "-" + node.getViewPoint());

        node.setNodeToken(nodeToken);

        if (getNextProcessChain() != null) {
            getNextProcessChain().doFilter(spanEntry, node, costMap);
        }
    }
}
