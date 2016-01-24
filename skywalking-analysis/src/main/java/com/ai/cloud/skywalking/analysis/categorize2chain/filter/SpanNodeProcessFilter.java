package com.ai.cloud.skywalking.analysis.categorize2chain.filter;

import com.ai.cloud.skywalking.analysis.categorize2chain.SubLevelSpanCostCounter;
import com.ai.cloud.skywalking.analysis.categorize2chain.SpanEntry;
import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainNode;

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
