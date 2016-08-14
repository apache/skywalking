package com.a.eye.skywalking.analysis.chainbuild.filter.impl;

import com.a.eye.skywalking.analysis.chainbuild.SpanEntry;
import com.a.eye.skywalking.analysis.chainbuild.po.ChainNode;
import com.a.eye.skywalking.analysis.chainbuild.filter.SpanNodeProcessFilter;
import com.a.eye.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;

public class CopyAttrFilter extends SpanNodeProcessFilter {

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {
        node.setCallType(spanEntry.getCallType().toString());
        node.setStatus(spanEntry.getSpanStatus());
        node.setLevelId(spanEntry.getLevelId());
        node.setParentLevelId(spanEntry.getParentLevelId());
        node.setViewPoint(spanEntry.getViewPoint());
        node.setUserId(spanEntry.getUserId());
        node.setBusinessKey(spanEntry.getBusinessKey());
        node.setStartDate(spanEntry.getStartDate());

        this.doNext(spanEntry, node, costMap);
    }
}
