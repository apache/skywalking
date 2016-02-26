package com.ai.cloud.skywalking.analysis.categorize2chain.filter.impl;

import com.ai.cloud.skywalking.analysis.categorize2chain.SpanEntry;
import com.ai.cloud.skywalking.analysis.categorize2chain.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.categorize2chain.po.ChainNode;
import com.ai.cloud.skywalking.analysis.categorize2chain.util.SubLevelSpanCostCounter;
import com.ai.cloud.skywalking.analysis.categorize2chain.util.TokenGenerator;

public class TokenGenerateFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {

        String nodeToken = TokenGenerator.generateNodeToken(node.getParentLevelId() + "." + node.getLevelId() +
                "-" + node.getViewPoint());

        node.setNodeToken(nodeToken);

        this.doNext(spanEntry, node, costMap);
    }
}
