package com.ai.cloud.skywalking.analysis.chain2summary;

import java.io.IOException;
import java.util.Map;

public class Summary {
    private Map<String, ChainSummaryWithRelationship> stringChainSummaryWithRelationshipMap;

    public void summary(ChainSpecificTimeSummary timeSummary, ChainRelationship4Search chainRelationship) throws IOException {
        String cid = chainRelationship.searchRelationship(timeSummary.getcId());
        if (cid == null || cid.length() == 0) {
            cid = timeSummary.getcId();
        }

        if (!stringChainSummaryWithRelationshipMap.containsKey(cid)) {
            stringChainSummaryWithRelationshipMap.put(cid, new ChainSummaryWithRelationship(cid));
        }

        ChainSummaryWithRelationship chainSummaryWithRelationship = stringChainSummaryWithRelationshipMap.get(cid);
        chainSummaryWithRelationship.summary(timeSummary);
    }

    public void saveToHBase() throws IOException, InterruptedException {
        for (Map.Entry<String, ChainSummaryWithRelationship> entry : stringChainSummaryWithRelationshipMap.entrySet()) {
            entry.getValue().saveToHBase();
        }
    }
}
