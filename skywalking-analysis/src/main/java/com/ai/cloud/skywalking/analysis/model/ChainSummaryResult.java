package com.ai.cloud.skywalking.analysis.model;

import com.ai.cloud.skywalking.analysis.util.HBaseUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChainSummaryResult {

    private Map<String, ChainNodeSummaryResult> chainNodeSummaryResultMap;

    public ChainSummaryResult() {
        chainNodeSummaryResultMap = new HashMap<String, ChainNodeSummaryResult>();
    }

    public static ChainSummaryResult load(String id) {
        ChainSummaryResult result = null;
        try {
            result = HBaseUtil.selectChainSummaryResult(id);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (result == null) {
            result = new ChainSummaryResult();
        }
        return result;
    }

    public void summaryResult(ChainInfo chainInfo) {
        for (ChainNode node : chainInfo.getNodes()) {
            ChainNodeSummaryResult chainNodeSummaryResult = chainNodeSummaryResultMap.get(node.getTraceLevelId());
            chainNodeSummaryResult.summary(node);
        }
    }

    public void addNodeSummaryResult(ChainNodeSummaryResult chainNodeSummaryResult) {
        chainNodeSummaryResultMap.put(chainNodeSummaryResult.getTraceLevelId(), chainNodeSummaryResult);
    }
}
