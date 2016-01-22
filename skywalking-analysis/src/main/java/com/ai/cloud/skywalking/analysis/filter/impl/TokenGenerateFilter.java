package com.ai.cloud.skywalking.analysis.filter.impl;

import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.mapper.CostMap;
import com.ai.cloud.skywalking.analysis.mapper.SpanEntry;
import com.ai.cloud.skywalking.analysis.util.TokenGenerator;

public class TokenGenerateFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, CostMap costMap) {

        String nodeToken = TokenGenerator.generate(node.getParentLevelId() + "." + node.getLevelId() +
                "-" + node.getViewPoint());

        node.setNodeToken(nodeToken);

        this.doNext(spanEntry, node, costMap);
    }
}
