package com.ai.cloud.skywalking.analysis.filter;

import com.ai.cloud.skywalking.analysis.filter.impl.CopyAttrNodeFilter;
import com.ai.cloud.skywalking.analysis.filter.impl.CostNodeFilter;
import com.ai.cloud.skywalking.analysis.filter.impl.ViewPointNodeFilter;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.protocol.Span;

import java.util.ArrayList;
import java.util.List;

public class NodeChain {

    private int stage = 0;

    private static List<ChainNodeFilter> filters = null;

    public void doChain(Span span, ChainNode chainNode, CostMap costMap) {
        if (filters == null) {
            filters = new ArrayList<ChainNodeFilter>();
            init();
        }

        int subscript = stage;
        stage = stage + 1;
        if (subscript < filters.size()) {
            filters.get(subscript).doFilter(span, chainNode, costMap, this);
        }
    }

    private static void init() {
        filters.add(new CostNodeFilter());
        filters.add(new CopyAttrNodeFilter());
        filters.add(new ViewPointNodeFilter());
    }
}
