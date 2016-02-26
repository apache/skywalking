package com.ai.cloud.skywalking.analysis.chain2summary.entity;

import com.ai.cloud.skywalking.analysis.categorize2chain.entity.ChainNodeSpecificTimeWindowSummary;
import com.ai.cloud.skywalking.analysis.chain2summary.po.ChainSpecificTimeSummary;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;

import org.apache.hadoop.hbase.client.Put;

import java.util.HashMap;
import java.util.Map;

public class ChainSpecificMinSummary {

    // Key: TraceLevelId
    private Map<String, ChainNodeSpecificMinSummary> chainNodeSpecificMinSummaryMap;

    public ChainSpecificMinSummary() {
        this.chainNodeSpecificMinSummaryMap = new HashMap<String, ChainNodeSpecificMinSummary>();
    }

    public void addNodeSummaryResult(ChainNodeSpecificMinSummary chainNodeSpecificMinSummary) {
        chainNodeSpecificMinSummaryMap.put(chainNodeSpecificMinSummary.getTraceLevelId(), chainNodeSpecificMinSummary);
    }

    public void summary(ChainSpecificTimeSummary timeSummary) {
        Map<String, ChainNodeSpecificTimeWindowSummary> chainNodeSpecificTimeWindowSummaryMap = timeSummary.getSummaryMap();

        for (Map.Entry<String, ChainNodeSpecificTimeWindowSummary> entry : chainNodeSpecificTimeWindowSummaryMap.entrySet()) {
            if (chainNodeSpecificMinSummaryMap.get(entry.getKey()) == null){
                chainNodeSpecificMinSummaryMap.put(entry.getKey(), new ChainNodeSpecificMinSummary());
            }
            chainNodeSpecificMinSummaryMap.get(entry.getKey()).summary(entry.getValue());
        }
    }

    public void save(Put put) {
        for (Map.Entry<String, ChainNodeSpecificMinSummary> entry : chainNodeSpecificMinSummaryMap.entrySet()) {
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_INCLUDE_RELATIONSHIP.
                            COLUMN_FAMILY_NAME.getBytes(), entry.getKey().getBytes(),
                    entry.getValue().toString().getBytes());
        }
    }
}
