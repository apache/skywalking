package com.ai.cloud.skywalking.analysis.categorize2chain.filter;

import com.ai.cloud.skywalking.analysis.categorize2chain.CostMap;
import com.ai.cloud.skywalking.analysis.categorize2chain.SpanEntry;
import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainNode;

public abstract class SpanNodeProcessFilter {

    private SpanNodeProcessFilter nextProcessChain;

    public abstract void doFilter(SpanEntry spanEntry, ChainNode node, CostMap costMap);

    protected void doNext(SpanEntry spanEntry, ChainNode node, CostMap costMap){
    	if(nextProcessChain != null){
    		nextProcessChain.doFilter(spanEntry, node, costMap);
    	}
    }

    void setNextProcessChain(SpanNodeProcessFilter nextProcessChain) {
        this.nextProcessChain = nextProcessChain;
    }
}
