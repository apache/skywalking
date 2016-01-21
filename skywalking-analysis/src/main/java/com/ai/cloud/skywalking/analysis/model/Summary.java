package com.ai.cloud.skywalking.analysis.model;

import com.ai.cloud.skywalking.analysis.util.HBaseUtil;
import org.apache.hadoop.hbase.client.Put;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Summary {

    private Map<String, ChainSpecificTimeWindowSummary> summaryResultMap;

    public Summary() {
        summaryResultMap = new HashMap<String, ChainSpecificTimeWindowSummary>();
    }

    public void summary(ChainInfo chainInfo) {
        for (ChainNode node : chainInfo.getNodes()) {
            String csk = generateChainSummaryKey(chainInfo.getChainToken(), node.getStartDate());
            ChainSpecificTimeWindowSummary chainSummaryResult = summaryResultMap.get(csk);
            if (chainSummaryResult == null) {
                chainSummaryResult = ChainSpecificTimeWindowSummary.load(csk);
                summaryResultMap.put(csk, chainSummaryResult);
            }

            chainSummaryResult.summaryResult(node);
        }
    }

    private String generateChainSummaryKey(String chainToken, long startDate) {
        return chainToken + "-" + (startDate / (1000 * 60 * 60));
    }

    public void save() throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainSpecificTimeWindowSummary> entry : summaryResultMap.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().save(put);
            puts.add(put);
        }

        HBaseUtil.batchSaveChainSpecificTimeWindowSummary(puts);
    }
}
