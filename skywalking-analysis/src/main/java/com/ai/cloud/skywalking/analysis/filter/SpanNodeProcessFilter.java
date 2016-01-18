package com.ai.cloud.skywalking.analysis.filter;

import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;
import com.ai.cloud.skywalking.protocol.Span;

public abstract class SpanNodeProcessFilter {

    private SpanNodeProcessFilter nextProcessChain;

    public abstract void doFilter(SpanEntry spanEntry, ChainNode node, CostMap costMap);

    public SpanNodeProcessFilter getNextProcessChain() {
        return nextProcessChain;
    }

    public void setNextProcessChain(SpanNodeProcessFilter nextProcessChain) {
        this.nextProcessChain = nextProcessChain;
    }
}
