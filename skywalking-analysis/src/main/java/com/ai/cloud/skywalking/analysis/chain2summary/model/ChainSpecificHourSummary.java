package com.ai.cloud.skywalking.analysis.chain2summary.model;

import com.ai.cloud.skywalking.analysis.categorize2chain.ChainNodeSpecificTimeWindowSummary;
import com.ai.cloud.skywalking.analysis.chain2summary.ChainSpecificTimeSummary;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import org.apache.hadoop.hbase.client.Put;

import java.util.HashMap;
import java.util.Map;

public class ChainSpecificHourSummary {
    // key : TraceLevelId
    private Map<String, ChainNodeSpecificHourSummary> chainNodeSpecificHourSummaryMap;

    public ChainSpecificHourSummary() {
        chainNodeSpecificHourSummaryMap = new HashMap<String, ChainNodeSpecificHourSummary>();
    }

    public void addNodeSummaryResult(ChainNodeSpecificHourSummary chainNodeSpecificHourSummary) {
        chainNodeSpecificHourSummaryMap.put(chainNodeSpecificHourSummary.getTraceLevelId(), chainNodeSpecificHourSummary);
    }

    public void summary(ChainSpecificTimeSummary timeSummary) {
        Map<String, ChainNodeSpecificTimeWindowSummary> chainNodeSpecificTimeWindowSummaryMap = timeSummary.getSummaryMap();

        for (Map.Entry<String, ChainNodeSpecificTimeWindowSummary> entry : chainNodeSpecificTimeWindowSummaryMap.entrySet()){
            if (chainNodeSpecificHourSummaryMap.get(entry.getKey()) == null){
                chainNodeSpecificHourSummaryMap.put(entry.getKey(), new ChainNodeSpecificHourSummary());
            }
            chainNodeSpecificHourSummaryMap.get(entry.getKey()).summary(timeSummary.getSummaryTimestamp(),entry.getValue());
        }
    }

    public void save(Put put) {
        for (Map.Entry<String, ChainNodeSpecificHourSummary> entry : chainNodeSpecificHourSummaryMap.entrySet()) {
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY_INCLUDE_RELATIONSHIP.
                            COLUMN_FAMILY_NAME.getBytes(), entry.getKey().getBytes(),
                    entry.getValue().toString().getBytes());
        }
    }
}
