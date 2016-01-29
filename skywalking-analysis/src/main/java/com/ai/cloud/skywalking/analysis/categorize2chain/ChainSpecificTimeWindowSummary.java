package com.ai.cloud.skywalking.analysis.categorize2chain;

import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainNode;
import com.ai.cloud.skywalking.analysis.categorize2chain.util.HBaseUtil;
import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;

import org.apache.hadoop.hbase.client.Put;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChainSpecificTimeWindowSummary {

	/**
	 * key : cid + 时间窗口
	 */
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

    public void addNodeSummaryResult(ChainNodeSpecificTimeWindowSummary chainNodeSummaryResult) {
        chainNodeSummaryResultMap.put(chainNodeSummaryResult.getTraceLevelId(), chainNodeSummaryResult);
    }

    public void summaryNodeValue(ChainNode node) {
        String tlid = node.getTraceLevelId();
        ChainNodeSpecificTimeWindowSummary chainNodeSummaryResult = chainNodeSummaryResultMap.get(tlid);
        if (chainNodeSummaryResult == null) {
            chainNodeSummaryResult = ChainNodeSpecificTimeWindowSummary.newInstance(tlid);
            chainNodeSummaryResultMap.put(tlid, chainNodeSummaryResult);
        }
        chainNodeSummaryResult.summary(node);
    }

    public void save(Put put) {
        for (Map.Entry<String, ChainNodeSpecificTimeWindowSummary> entry : chainNodeSummaryResultMap.entrySet()) {
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP.COLUMN_FAMILY_NAME.getBytes(), entry.getKey().getBytes(), entry.getValue().toString().getBytes());
        }
    }
}
