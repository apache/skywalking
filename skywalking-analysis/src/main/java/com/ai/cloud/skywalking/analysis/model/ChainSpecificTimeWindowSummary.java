package com.ai.cloud.skywalking.analysis.model;

import com.ai.cloud.skywalking.analysis.util.HBaseUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChainSpecificTimeWindowSummary {

    private Map<String, ChainNodeSpecificTimeWindowSummary> chainNodeSummaryResultMap;

    public ChainSpecificTimeWindowSummary() {
        chainNodeSummaryResultMap = new HashMap<String, ChainNodeSpecificTimeWindowSummary>();
    }

    public static ChainSpecificTimeWindowSummary load(String cid_time) {
        ChainSpecificTimeWindowSummary result = null;
        try {
            result = HBaseUtil.selectChainSummaryResult(cid_time);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (result == null) {
            result = new ChainSpecificTimeWindowSummary();
        }
        return result;
    }

    public void summaryResult(ChainInfo chainInfo) {
        for (ChainNode node : chainInfo.getNodes()) {
        	String tlid = node.getTraceLevelId();
            ChainNodeSpecificTimeWindowSummary chainNodeSummaryResult = chainNodeSummaryResultMap.get(tlid);
            if(chainNodeSummaryResult == null){
            	chainNodeSummaryResult = ChainNodeSpecificTimeWindowSummary.newInstance(tlid);
            }
            chainNodeSummaryResult.summary(node);
        }
    }

    public void addNodeSummaryResult(ChainNodeSpecificTimeWindowSummary chainNodeSummaryResult) {
        chainNodeSummaryResultMap.put(chainNodeSummaryResult.getTraceLevelId(), chainNodeSummaryResult);
    }
}
