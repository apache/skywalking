package com.ai.cloud.skywalking.analysis.categorize2chain.filter.impl;

import com.ai.cloud.skywalking.analysis.categorize2chain.CostMap;
import com.ai.cloud.skywalking.analysis.categorize2chain.SpanEntry;
import com.ai.cloud.skywalking.analysis.categorize2chain.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainNode;
import com.ai.cloud.skywalking.analysis.util.TokenGenerator;

public class TokenGenerateFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, CostMap costMap) {

        String nodeToken = TokenGenerator.generateNodeToken(node.getParentLevelId() + "." + node.getLevelId() +
                "-" + node.getViewPoint());

        node.setNodeToken(nodeToken);

        this.doNext(spanEntry, node, costMap);
    }
}
