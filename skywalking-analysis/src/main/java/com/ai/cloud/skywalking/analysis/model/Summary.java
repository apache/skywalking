package com.ai.cloud.skywalking.analysis.model;

import java.util.Map;

public class Summary {

    private Map<String, ChainSpecificTimeWindowSummary> summaryResultMap;

    public void summary(ChainInfo chainInfo) {
        String csk = generateChainSummaryKey(chainInfo);
        ChainSpecificTimeWindowSummary chainSummaryResult = summaryResultMap.get(csk);
        if (chainSummaryResult == null) {
            chainSummaryResult = ChainSpecificTimeWindowSummary.load(csk);
            summaryResultMap.put(csk, chainSummaryResult);
        }

        chainSummaryResult.summaryResult(chainInfo);
    }

    private String generateChainSummaryKey(ChainInfo chainInfo) {
        return chainInfo.getChainToken() + ":" + (chainInfo.getStartDate() / (1000 * 60 * 60));
    }

    public void save() {

    }
}
