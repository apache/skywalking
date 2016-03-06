package com.ai.cloud.skywalking.analysis.chainbuild.filter.impl;

import com.ai.cloud.skywalking.analysis.chainbuild.SpanEntry;
import com.ai.cloud.skywalking.analysis.chainbuild.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;
import com.ai.cloud.skywalking.analysis.chainbuild.util.TokenGenerator;

public class TokenGenerateFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {

        String nodeToken = TokenGenerator.generateNodeToken(node.getParentLevelId() + "." + node.getLevelId() +
                "-" + node.getViewPoint());

        node.setNodeToken(nodeToken);

        this.doNext(spanEntry, node, costMap);
    }
}
