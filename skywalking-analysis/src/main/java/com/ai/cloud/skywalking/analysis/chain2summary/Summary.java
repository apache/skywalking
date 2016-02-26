package com.ai.cloud.skywalking.analysis.chain2summary;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ai.cloud.skywalking.analysis.chain2summary.entity.ChainSummaryWithRelationship;
import com.ai.cloud.skywalking.analysis.chain2summary.po.ChainSpecificTimeSummary;

public class Summary {
    private Map<String, ChainSummaryWithRelationship> summaryWithRelationshipMap;

    public  Summary(){
        summaryWithRelationshipMap = new ConcurrentHashMap<String, ChainSummaryWithRelationship>();
    }

    public void summary(ChainSpecificTimeSummary timeSummary, ChainRelationship4Search chainRelationship) throws IOException {
        String cid = chainRelationship.searchRelationship(timeSummary.getcId());
        if (cid == null || cid.length() == 0) {
            cid = timeSummary.getcId();
        }

        if (!summaryWithRelationshipMap.containsKey(cid)) {
            summaryWithRelationshipMap.put(cid, new ChainSummaryWithRelationship(cid));
        }

        ChainSummaryWithRelationship chainSummaryWithRelationship = summaryWithRelationshipMap.get(cid);
        chainSummaryWithRelationship.summary(timeSummary);
    }

    public void saveToHBase() throws IOException, InterruptedException {
        for (Map.Entry<String, ChainSummaryWithRelationship> entry : summaryWithRelationshipMap.entrySet()) {
            entry.getValue().saveToHBase();
        }
    }
}
