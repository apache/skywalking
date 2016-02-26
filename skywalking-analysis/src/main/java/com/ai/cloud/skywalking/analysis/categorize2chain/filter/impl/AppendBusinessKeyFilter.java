package com.ai.cloud.skywalking.analysis.categorize2chain.filter.impl;

import com.ai.cloud.skywalking.analysis.categorize2chain.SpanEntry;
import com.ai.cloud.skywalking.analysis.categorize2chain.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.categorize2chain.po.ChainNode;
import com.ai.cloud.skywalking.analysis.categorize2chain.util.SubLevelSpanCostCounter;

public class AppendBusinessKeyFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {
        node.setViewPoint(node.getViewPoint() + spanEntry.getBusinessKey());

        this.doNext(spanEntry, node, costMap);
    }
}
