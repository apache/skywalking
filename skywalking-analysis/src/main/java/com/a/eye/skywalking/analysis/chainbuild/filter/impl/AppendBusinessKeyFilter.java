package com.a.eye.skywalking.analysis.chainbuild.filter.impl;

import com.a.eye.skywalking.analysis.chainbuild.SpanEntry;
import com.a.eye.skywalking.analysis.chainbuild.po.ChainNode;
import com.a.eye.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;
import com.a.eye.skywalking.analysis.chainbuild.filter.SpanNodeProcessFilter;

public class AppendBusinessKeyFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {
        node.setViewPoint(node.getViewPoint() + spanEntry.getBusinessKey());

        this.doNext(spanEntry, node, costMap);
    }
}
