package com.a.eye.skywalking.analysis.chainbuild.filter.impl;

import com.a.eye.skywalking.analysis.chainbuild.SpanEntry;
import com.a.eye.skywalking.analysis.chainbuild.po.ChainNode;
import com.a.eye.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;
import com.a.eye.skywalking.analysis.chainbuild.util.TokenGenerator;
import com.a.eye.skywalking.analysis.chainbuild.filter.SpanNodeProcessFilter;

public class TokenGenerateFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {

        String nodeToken = TokenGenerator.generateNodeToken(node.getParentLevelId() + "." + node.getLevelId() +
                "-" + node.getViewPoint());

        node.setNodeToken(nodeToken);

        this.doNext(spanEntry, node, costMap);
    }
}
