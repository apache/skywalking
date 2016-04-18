package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.HBaseUtil;
import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import org.apache.hadoop.hbase.client.Put;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * 调用树的每个traceLevelId + "@" + viewPointId构成一个树节点<br/>
 * 虚拟化节点概念。节点存储落地时，按照节点对应的时间戳<br/>
 *
 * @author wusheng
 */
public class CallChainTreeNode {
    private Logger logger = LogManager.getLogger(CallChainTreeNode.class);

    @Expose
    private String traceLevelId;
    @Expose
    private String viewPointId;

    /**
     * key: treeId + 小时
     * value: 当前树的当前小时范围内的，所有分钟和节点的统计数据
     */
    private Map<String, ChainNodeSpecificMinSummary> chainNodeSpecificMinSummaryContainer;

    /**
     * key: treeId + 天
     * value: 当前树的当前天范围内的，所有小时和节点的统计数据
     */
    private Map<String, ChainNodeSpecificHourSummary> chainNodeSpecificHourSummaryContainer;

    /**
     * key: treeId + 月
     * value: 当前树的当前月范围内的，所有天和节点的统计数据
     */
    private Map<String, ChainNodeSpecificDaySummary> chainNodeSpecificDaySummaryContainer;

    /**
     * key: treeId + 年
     * value: 当前树的当前年范围内的，所有月份和节点的统计数据
     */
    private Map<String, ChainNodeSpecificMonthSummary> chainNodeSpecificMonthSummaryContainer;

    public CallChainTreeNode(ChainNode node) {
        this.traceLevelId = node.getTraceLevelId();
        chainNodeSpecificMinSummaryContainer = new HashMap<String, ChainNodeSpecificMinSummary>();
        chainNodeSpecificHourSummaryContainer = new HashMap<String, ChainNodeSpecificHourSummary>();
        chainNodeSpecificDaySummaryContainer = new HashMap<String, ChainNodeSpecificDaySummary>();
        chainNodeSpecificMonthSummaryContainer = new HashMap<String, ChainNodeSpecificMonthSummary>();
        this.viewPointId = node.getViewPoint();
    }

    public void summary(String treeId, ChainNode node) throws IOException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(node.getStartDate()));

        summaryMinResult(treeId, node, calendar);
        summaryHourResult(treeId, node, calendar);
        summaryDayResult(treeId, node, calendar);
        summaryMonthResult(treeId, node, calendar);
    }

    private void summaryMonthResult(String treeId, ChainNode node, Calendar calendar) throws IOException {
        String keyOfMonthSummaryTable = generateKeyOfMonthSummaryTable(treeId, calendar);
        ChainNodeSpecificMonthSummary monthSummary = chainNodeSpecificMonthSummaryContainer.get(keyOfMonthSummaryTable);
        if (monthSummary == null) {
            if (Config.AnalysisServer.IS_ACCUMULATE_MODE) {
                monthSummary = HBaseUtil.loadSpecificMonthSummary(keyOfMonthSummaryTable, getTreeNodeId());
            } else {
                monthSummary = new ChainNodeSpecificMonthSummary();
            }
            chainNodeSpecificMonthSummaryContainer.put(keyOfMonthSummaryTable, monthSummary);
        }
        monthSummary.summary(String.valueOf(calendar.get(Calendar.MONTH)), node);
    }

    private void summaryDayResult(String treeId, ChainNode node, Calendar calendar) throws IOException {
        String keyOfDaySummaryTable = generateKeyOfDaySummaryTable(treeId, calendar);
        ChainNodeSpecificDaySummary daySummary = chainNodeSpecificDaySummaryContainer.get(keyOfDaySummaryTable);
        if (daySummary == null) {
            if (Config.AnalysisServer.IS_ACCUMULATE_MODE) {
                daySummary = HBaseUtil.loadSpecificDaySummary(keyOfDaySummaryTable, getTreeNodeId());
            } else {
                daySummary = new ChainNodeSpecificDaySummary();
            }
            chainNodeSpecificDaySummaryContainer.put(keyOfDaySummaryTable, daySummary);
        }
        daySummary.summary(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)), node);
    }

    private void summaryHourResult(String treeId, ChainNode node, Calendar calendar) throws IOException {
        String keyOfHourSummaryTable = generateKeyOfHourSummaryTable(treeId, calendar);
        ChainNodeSpecificHourSummary hourSummary = chainNodeSpecificHourSummaryContainer.get(keyOfHourSummaryTable);
        if (hourSummary == null) {
            if (Config.AnalysisServer.IS_ACCUMULATE_MODE) {
                hourSummary = HBaseUtil.loadSpecificHourSummary(keyOfHourSummaryTable, getTreeNodeId());
            } else {
                hourSummary = new ChainNodeSpecificHourSummary();
            }
            chainNodeSpecificHourSummaryContainer.put(keyOfHourSummaryTable, hourSummary);
        }
        hourSummary.summary(String.valueOf(calendar.get(Calendar.HOUR)), node);
    }

    /**
     * 按分钟维度进行汇总<br/>
     * chainNodeContainer以treeId和时间（精确到分钟）为key，value为当前时间范围内的所有分钟的汇总数据
     */
    private void summaryMinResult(String treeId, ChainNode node, Calendar calendar) throws IOException {
        String keyOfMinSummaryTable = generateKeyOfMinSummaryTable(treeId, calendar);
        ChainNodeSpecificMinSummary minSummary = chainNodeSpecificMinSummaryContainer.get(keyOfMinSummaryTable);
        if (minSummary == null) {
            if (Config.AnalysisServer.IS_ACCUMULATE_MODE) {
                minSummary = HBaseUtil.loadSpecificMinSummary(keyOfMinSummaryTable, getTreeNodeId());
            } else {
                minSummary = new ChainNodeSpecificMinSummary();
            }
            chainNodeSpecificMinSummaryContainer.put(keyOfMinSummaryTable, minSummary);
        }
        minSummary.summary(String.valueOf(calendar.get(Calendar.MINUTE)), node);
    }

    private String generateKeyOfMonthSummaryTable(String treeId, Calendar calendar) {
        return treeId + "/" + calendar.get(Calendar.YEAR);
    }

    private String generateKeyOfDaySummaryTable(String treeId, Calendar calendar) {
        return treeId + "/" + calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1);
    }

    private String generateKeyOfHourSummaryTable(String treeId, Calendar calendar) {
        return treeId + "/" + calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-"
                + calendar.get(Calendar.DAY_OF_MONTH);
    }

    private String generateKeyOfMinSummaryTable(String treeId, Calendar calendar) {
        return treeId + "/" + calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-"
                + calendar.get(Calendar.DAY_OF_MONTH) + " " + calendar.get(Calendar.HOUR) + ":00:00";
    }

    @Override
    public String toString() {
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(this);
    }

    /**
     * 存储入库时 <br/>
     * hbase的key 为 treeId + 小时 <br/>
     * 列族中，列为节点id，规则为：traceLevelId + "@" + viewPointId <br/>
     * 列的值，为当前节点按小时内各分钟的汇总 <br/>
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void saveSummaryResultToHBase() throws IOException, InterruptedException {
        batchSaveMinSummaryResult();
        batchSaveHourSummaryResult();
        batchSaveDaySummaryResult();
        batchSaveMonthSummaryResult();
    }

    private void batchSaveMonthSummaryResult() throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainNodeSpecificMonthSummary> entry : chainNodeSpecificMonthSummaryContainer.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY.COLUMN_FAMILY_NAME.getBytes()
                    , getTreeNodeId().getBytes(), entry.getValue().toString().getBytes());
            puts.add(put);
        }

        HBaseUtil.batchSaveMonthSummaryResult(puts);
    }


    private void batchSaveDaySummaryResult() throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainNodeSpecificDaySummary> entry : chainNodeSpecificDaySummaryContainer.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY.COLUMN_FAMILY_NAME.getBytes()
                    , getTreeNodeId().getBytes(), entry.getValue().toString().getBytes());
            puts.add(put);
        }

        HBaseUtil.batchSaveDaySummaryResult(puts);
    }

    private void batchSaveHourSummaryResult() throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainNodeSpecificHourSummary> entry : chainNodeSpecificHourSummaryContainer.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY.COLUMN_FAMILY_NAME.getBytes()
                    , getTreeNodeId().getBytes(), entry.getValue().toString().getBytes());
            puts.add(put);
        }

        HBaseUtil.batchSaveHourSummaryResult(puts);
    }

    private void batchSaveMinSummaryResult() throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainNodeSpecificMinSummary> entry : chainNodeSpecificMinSummaryContainer.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.COLUMN_FAMILY_NAME.getBytes()
                    , getTreeNodeId().getBytes(), entry.getValue().toString().getBytes());
            puts.add(put);
        }

        HBaseUtil.batchSaveMinSummaryResult(puts);
    }

    public String getTreeNodeId() {
        return traceLevelId + "@" + viewPointId;
    }
}
