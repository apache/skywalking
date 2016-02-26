package com.ai.cloud.skywalking.analysis.chain2summary.entity;

import com.ai.cloud.skywalking.analysis.categorize2chain.entity.ChainNodeSpecificTimeWindowSummary;
import com.ai.cloud.skywalking.analysis.chain2summary.po.ChainSpecificTimeSummary;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;

import org.apache.hadoop.hbase.client.Put;

import java.util.HashMap;
import java.util.Map;

public class ChainSpecificDaySummary {
    private Map<String, ChainNodeSpecificDaySummary> chainNodeSpecificHourSummaryMap;

    public ChainSpecificDaySummary() {
        chainNodeSpecificHourSummaryMap = new HashMap<String, ChainNodeSpecificDaySummary>();
    }

    public void addNodeSummaryResult(ChainNodeSpecificDaySummary chainNodeSpecificHourSummary) {
        chainNodeSpecificHourSummaryMap.put(chainNodeSpecificHourSummary.getTraceLevelId(), chainNodeSpecificHourSummary);
    }

    public void summary(ChainSpecificTimeSummary timeSummary) {
        Map<String, ChainNodeSpecificTimeWindowSummary> chainNodeSpecificTimeWindowSummaryMap = timeSummary.getSummaryMap();

        for (Map.Entry<String, ChainNodeSpecificTimeWindowSummary> entry : chainNodeSpecificTimeWindowSummaryMap.entrySet()) {
            if (chainNodeSpecificHourSummaryMap.get(entry.getKey()) == null){
                chainNodeSpecificHourSummaryMap.put(entry.getKey(), new ChainNodeSpecificDaySummary());
            }
            chainNodeSpecificHourSummaryMap.get(entry.getKey()).summary(timeSummary.getSummaryTimestamp(), entry.getValue());
        }
    }

    public void save(Put put) {
        for (Map.Entry<String, ChainNodeSpecificDaySummary> entry : chainNodeSpecificHourSummaryMap.entrySet()) {
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY_INCLUDE_RELATIONSHIP.
                            COLUMN_FAMILY_NAME.getBytes(), entry.getKey().getBytes(),
                    entry.getValue().toString().getBytes());
        }
    }
}
