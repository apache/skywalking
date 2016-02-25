package com.ai.cloud.skywalking.analysis.chain2summary.model;

import com.ai.cloud.skywalking.analysis.categorize2chain.ChainNodeSpecificTimeWindowSummary;
import com.ai.cloud.skywalking.analysis.chain2summary.ChainSpecificTimeSummary;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import org.apache.hadoop.hbase.client.Put;

import java.util.Map;

public class ChainSpecificMonthSummary {

    // Key: TraceLevelId
    private Map<String, ChainNodeSpecificMonthSummary> chainNodeSpecificMinSummaryMap;

    public void addNodeSummaryResult(ChainNodeSpecificMonthSummary chainNodeSpecificDaySummary) {
        chainNodeSpecificMinSummaryMap.put(chainNodeSpecificDaySummary.getTraceLevelId(), chainNodeSpecificDaySummary);
    }

    public void summary(ChainSpecificTimeSummary timeSummary) {
        Map<String, ChainNodeSpecificTimeWindowSummary> chainNodeSpecificTimeWindowSummaryMap = timeSummary.getSummaryMap();

        for (Map.Entry<String, ChainNodeSpecificTimeWindowSummary> entry : chainNodeSpecificTimeWindowSummaryMap.entrySet()) {
            if (chainNodeSpecificMinSummaryMap.get(entry.getKey()) == null){
                chainNodeSpecificMinSummaryMap.put(entry.getKey(), new ChainNodeSpecificMonthSummary());
            }
            chainNodeSpecificMinSummaryMap.get(entry.getKey()).summary(timeSummary.getSummaryTimestamp(), entry.getValue());
        }
    }

    public void save(Put put) {
        for (Map.Entry<String, ChainNodeSpecificMonthSummary> entry : chainNodeSpecificMinSummaryMap.entrySet()) {
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY_INCLUDE_RELATIONSHIP.
                            COLUMN_FAMILY_NAME.getBytes(), entry.getKey().getBytes(),
                    entry.getValue().toString().getBytes());
        }
    }
}
