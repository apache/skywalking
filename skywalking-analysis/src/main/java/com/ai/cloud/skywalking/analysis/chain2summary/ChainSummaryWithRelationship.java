package com.ai.cloud.skywalking.analysis.chain2summary;

import com.ai.cloud.skywalking.analysis.categorize2chain.util.HBaseUtil;
import com.ai.cloud.skywalking.analysis.chain2summary.model.ChainSpecificDaySummary;
import com.ai.cloud.skywalking.analysis.chain2summary.model.ChainSpecificHourSummary;
import com.ai.cloud.skywalking.analysis.chain2summary.model.ChainSpecificMinSummary;
import com.ai.cloud.skywalking.analysis.chain2summary.model.ChainSpecificMonthSummary;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChainSummaryWithRelationship {

    private String cid;
    // key : cid  + userId + 小时
    private Map<String, ChainSpecificMinSummary> minSummary;
    // key : cid  + userId + 天
    private Map<String, ChainSpecificHourSummary> hourSummary;
    // key : cid  + userId + 月
    private Map<String, ChainSpecificDaySummary> daySummary;
    // key : cid  + userId + 年
    private Map<String, ChainSpecificMonthSummary> monthSummary;

    public ChainSummaryWithRelationship(String cid) {
        this.cid = cid;
        minSummary = new HashMap<String, ChainSpecificMinSummary>();
        hourSummary = new HashMap<String, ChainSpecificHourSummary>();
        daySummary = new HashMap<String, ChainSpecificDaySummary>();
        monthSummary = new HashMap<String, ChainSpecificMonthSummary>();
    }

    public void saveToHBase() throws IOException, InterruptedException {
        HBaseUtil.batchSaveSpecificMinSummary(minSummary);
        HBaseUtil.batchSaveSpecificHourSummary(hourSummary);
        HBaseUtil.batchSaveSpecificDaySummary(daySummary);
        HBaseUtil.batchSaveSpecificMonthSummary(monthSummary);
    }

    public void summary(ChainSpecificTimeSummary timeSummary) throws IOException {
        loadSummaryIfNecessary(cid,timeSummary);
        //
        minSummary.get(buildMinSummaryRowKey(cid, timeSummary)).summary(timeSummary);
        hourSummary.get(buildHourSummaryRowKey(cid, timeSummary)).summary(timeSummary);
        daySummary.get(buildDaySummaryRowKey(cid, timeSummary)).summary(timeSummary);
        monthSummary.get(buildMonthSummaryRowKey(cid, timeSummary)).summary(timeSummary);
    }


    private void loadSummaryIfNecessary(String cid, ChainSpecificTimeSummary timeSummary) throws IOException {
        loadMinSummaryIfNecessary(cid, timeSummary);
        loadHourSummaryIfNecessary(cid, timeSummary);
        loadDaySummaryIfNecessary(cid, timeSummary);
        loadMonthSummaryIfNecessary(cid, timeSummary);
    }

    private void loadMonthSummaryIfNecessary(String cid, ChainSpecificTimeSummary timeSummary) throws IOException {
        String month_RowKey = buildMonthSummaryRowKey(cid, timeSummary);
        if (!monthSummary.containsKey(month_RowKey)) {
            monthSummary.put(month_RowKey, HBaseUtil.loadSpecificMonthSummary(month_RowKey));
        }
    }

    private void loadDaySummaryIfNecessary(String cid, ChainSpecificTimeSummary timeSummary) throws IOException {
        String day_RowKey = buildDaySummaryRowKey(cid, timeSummary);
        if (!daySummary.containsKey(day_RowKey)) {
            daySummary.put(day_RowKey, HBaseUtil.loadSpecificDaySummary(day_RowKey));
        }
    }

    private void loadHourSummaryIfNecessary(String cid, ChainSpecificTimeSummary timeSummary) throws IOException {
        String hour_RowKey = buildHourSummaryRowKey(cid, timeSummary);
        if (!hourSummary.containsKey(hour_RowKey)) {
            hourSummary.put(hour_RowKey, HBaseUtil.loadSpecificHourSummary(hour_RowKey));
        }
    }

    private void loadMinSummaryIfNecessary(String cid, ChainSpecificTimeSummary timeSummary) throws IOException {
        String min_RowKey = buildMinSummaryRowKey(cid, timeSummary);
        if (!minSummary.containsKey(min_RowKey)) {
            minSummary.put(min_RowKey, HBaseUtil.loadSpecificMinSummary(min_RowKey));
        }
    }

    // 月统计是以年作为RowKey的
    private static String buildMonthSummaryRowKey(String cid, ChainSpecificTimeSummary timeSummary) {
        return cid + "-" + timeSummary.getUserId() + "-" + timeSummary.getYearKey();
    }

    // 天统计是以月作为RowKey的
    private static String buildDaySummaryRowKey(String cid, ChainSpecificTimeSummary timeSummary) {
        return cid + "-" + timeSummary.getUserId() + "-" + timeSummary.getMonthKey();
    }

    // 小时统计是以天作为RowKey的
    private static String buildHourSummaryRowKey(String cid, ChainSpecificTimeSummary timeSummary) {
        return cid + "-" + timeSummary.getUserId() + "-" + timeSummary.getDayKey();
    }

    // 分钟统计是以小时作为RowKey的
    private static String buildMinSummaryRowKey(String cid, ChainSpecificTimeSummary timeSummary) {
        return cid + "-" + timeSummary.getUserId() + "-" + timeSummary.getHourKey();
    }
}
