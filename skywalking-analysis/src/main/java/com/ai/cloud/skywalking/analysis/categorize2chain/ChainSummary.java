package com.ai.cloud.skywalking.analysis.categorize2chain;

import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainNode;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;

import org.apache.hadoop.hbase.client.Put;

import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class ChainSummary {

    private Map<String, ChainSpecificTimeWindowSummary> summaryResultMap;
    private Map<String, Timestamp> updateChainInfo;

    public ChainSummary() {
        summaryResultMap = new HashMap<String, ChainSpecificTimeWindowSummary>();
        updateChainInfo = new HashMap<String, Timestamp>();
    }

    public void summary(ChainInfo chainInfo) {
        for (ChainNode node : chainInfo.getNodes()) {
            String csk = generateChainSummaryKey(chainInfo.getCID(), node.getStartDate());
            ChainSpecificTimeWindowSummary chainSummaryResult = summaryResultMap.get(csk);
            if (chainSummaryResult == null) {
                chainSummaryResult = ChainSpecificTimeWindowSummary.load(csk);
                summaryResultMap.put(csk, chainSummaryResult);
            }

            summaryResultMap.get(csk).summaryResult(node);
        }

        updateChainInfo.put(chainInfo.getCID(), new Timestamp(System.currentTimeMillis()));
    }

    private String generateChainSummaryKey(String chainToken, long startDate) {
        return chainToken + "-" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").
                format(new Date(startDate / (1000 * 60 * 60) * (1000 * 60 * 60)));
    }

    public void save() throws IOException, InterruptedException, SQLException {
        batchSaveChainSpecificTimeWindowSummary();
        batchUpdateChainDetail();
    }

    private void batchUpdateChainDetail() throws SQLException {
        DBCallChainInfoDao.updateChainDetail(updateChainInfo);
    }

    private void batchSaveChainSpecificTimeWindowSummary() throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainSpecificTimeWindowSummary> entry : summaryResultMap.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().save(put);
            puts.add(put);
        }

        HBaseUtil.batchSaveChainSpecificTimeWindowSummary(puts);
    }
}
