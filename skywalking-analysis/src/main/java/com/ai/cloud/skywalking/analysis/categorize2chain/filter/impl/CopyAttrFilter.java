package com.ai.cloud.skywalking.analysis.categorize2chain.filter.impl;

import com.ai.cloud.skywalking.analysis.categorize2chain.SpanEntry;
import com.ai.cloud.skywalking.analysis.categorize2chain.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.categorize2chain.po.ChainNode;
import com.ai.cloud.skywalking.analysis.categorize2chain.util.SubLevelSpanCostCounter;

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
