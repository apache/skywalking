package com.ai.cloud.skywalking.analysis.chainbuild.filter.impl;

import com.ai.cloud.skywalking.analysis.chainbuild.SpanEntry;
import com.ai.cloud.skywalking.analysis.chainbuild.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;

public class AppendBusinessKeyFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {
        node.setViewPoint(node.getViewPoint() + spanEntry.getBusinessKey());

        this.doNext(spanEntry, node, costMap);
    }
}
