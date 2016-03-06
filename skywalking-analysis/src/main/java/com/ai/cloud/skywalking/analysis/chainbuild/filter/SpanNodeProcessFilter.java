package com.ai.cloud.skywalking.analysis.chainbuild.filter;

import com.ai.cloud.skywalking.analysis.chainbuild.SpanEntry;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;

public abstract class SpanNodeProcessFilter {

    private SpanNodeProcessFilter nextProcessChain;

    public abstract void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap);

    protected void doNext(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap){
    	if(nextProcessChain != null){
    		nextProcessChain.doFilter(spanEntry, node, costMap);
    	}
    }

    void setNextProcessChain(SpanNodeProcessFilter nextProcessChain) {
        this.nextProcessChain = nextProcessChain;
    }
}
