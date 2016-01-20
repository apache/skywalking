package com.ai.cloud.skywalking.analysis.model;

import java.util.Map;

public class Summary {

    private Map<String, ChainSummaryResult> summaryResultMap;

    public void summary(ChainInfo chainInfo) {
        String key = generateKey(chainInfo);
        ChainSummaryResult chainSummaryResult = summaryResultMap.get(key);
        if (chainSummaryResult == null) {
            chainSummaryResult = ChainSummaryResult.load(key);
            summaryResultMap.put(key, chainSummaryResult);
        }

        chainSummaryResult.summaryResult(chainInfo);
    }

    private String generateKey(ChainInfo chainInfo) {
        return chainInfo.getChainToken() + ":" + (chainInfo.getStartDate() / (1000 * 60 * 60));
    }

    public void save() {

    }
}
