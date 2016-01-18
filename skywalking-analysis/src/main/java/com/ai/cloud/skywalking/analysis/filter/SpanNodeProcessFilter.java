package com.ai.cloud.skywalking.analysis.filter;

import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;

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
