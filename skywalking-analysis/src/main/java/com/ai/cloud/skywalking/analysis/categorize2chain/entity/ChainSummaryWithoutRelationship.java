package com.ai.cloud.skywalking.analysis.categorize2chain.entity;

import com.ai.cloud.skywalking.analysis.categorize2chain.DBCallChainInfoDao;
import com.ai.cloud.skywalking.analysis.categorize2chain.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.categorize2chain.po.ChainNode;
import com.ai.cloud.skywalking.analysis.categorize2chain.util.HBaseUtil;

import org.apache.hadoop.hbase.client.Put;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChainSummaryWithoutRelationship {

    private static Logger logger = LoggerFactory.getLogger(ChainSummaryWithoutRelationship.class.getName());
    private Map<String, ChainSpecificTimeWindowSummary> loadedChainSpecificTimeWindowSummary;
    private Map<String, Timestamp> updateChainInfo;

    public ChainSummaryWithoutRelationship() {
        loadedChainSpecificTimeWindowSummary = new HashMap<String, ChainSpecificTimeWindowSummary>();
        updateChainInfo = new HashMap<String, Timestamp>();
    }

    public void summary(ChainInfo chainInfo) {
        for (ChainNode node : chainInfo.getNodes()) {
            String csk = generateChainSummaryKey(chainInfo, node.getStartDate());
            if (!loadedChainSpecificTimeWindowSummary.containsKey(csk)) {
                loadedChainSpecificTimeWindowSummary.put(csk, ChainSpecificTimeWindowSummary.load(csk));
            }
            loadedChainSpecificTimeWindowSummary.get(csk).summaryNodeValue(node);
        }
        updateChainInfo.put(chainInfo.getCID(), new Timestamp(System.currentTimeMillis()));
    }

    private String generateChainSummaryKey(ChainInfo chainInfo, long startDate) {
        return chainInfo.getCID() + "-" + chainInfo.getUserId() + "-" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").
                format(new Date(startDate / (1000 * 60 * 60) * (1000 * 60 * 60)));
    }

    public void save() throws IOException, InterruptedException, SQLException {
        batchSaveChainSpecificTimeWindowSummary();
        updateChainLastActiveTime();
    }

    private void updateChainLastActiveTime() throws SQLException {
        DBCallChainInfoDao.updateChainLastActiveTime(updateChainInfo);
    }

    private void batchSaveChainSpecificTimeWindowSummary() throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        logger.info("There are [" + loadedChainSpecificTimeWindowSummary.size() + "] summary data will be storage to HBase");
        for (Map.Entry<String, ChainSpecificTimeWindowSummary> entry : loadedChainSpecificTimeWindowSummary.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().save(put);
            puts.add(put);
        }

        HBaseUtil.batchSaveChainSpecificTimeWindowSummary(puts);
    }
}
