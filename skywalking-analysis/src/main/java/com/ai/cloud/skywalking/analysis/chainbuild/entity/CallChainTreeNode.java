package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.HBaseUtil;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import org.apache.hadoop.hbase.client.Put;

import java.io.IOException;
import java.util.*;

public class CallChainTreeNode {
    @Expose
    private String traceLevelId;
    @Expose
    private String viewPointId;

    // key: treeId + 小时
    private Map<String, ChainNodeSpecificMinSummary> chainNodeContainer;


    public CallChainTreeNode(ChainNode node) {
        this.traceLevelId = node.getTraceLevelId();
        chainNodeContainer = new HashMap<String, ChainNodeSpecificMinSummary>();
        this.viewPointId = node.getViewPoint();
    }

    public CallChainTreeNode(String originData) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(originData);
        traceLevelId = jsonObject.get("traceLevelId").getAsString();
        viewPointId = jsonObject.get("viewPointId").getAsString();
        // 每次都只load对应的节点统计结果，不全部load出来
        chainNodeContainer = new HashMap<String, ChainNodeSpecificMinSummary>();
    }

    public void mergeIfNess(ChainNode node) {
        if (!chainNodeContainer.containsKey(node.getNodeToken())) {
            chainNodeContainer.put(node.getNodeToken(), new ChainNodeSpecificMinSummary());
        }
    }

    public void summary(String treeId, ChainNode node) throws IOException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(node.getStartDate()));
        // summary min
        String keyOfMinSummaryTable = generateKeyOfMinSummaryTable(treeId, calendar);
        ChainNodeSpecificMinSummary minSummary = chainNodeContainer.get(keyOfMinSummaryTable);
        if (minSummary == null) {
            minSummary = HBaseUtil.loadSpecificMinSummary(keyOfMinSummaryTable, node.getTraceLevelId());
            chainNodeContainer.put(keyOfMinSummaryTable, minSummary);
        }

        minSummary.summary(String.valueOf(calendar.get(Calendar.MINUTE)), node);
    }

    private String generateKeyOfMinSummaryTable(String treeId, Calendar calendar) {
        return treeId + "/" + calendar.get(Calendar.YEAR) + "-" + calendar.get(Calendar.MONTH) + "-"
                + calendar.get(Calendar.DAY_OF_MONTH) + " " + calendar.get(Calendar.HOUR) + ":00:00";
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    @Override
    public String toString() {
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(this);
    }

    public void saveSummaryResultToHBase() throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainNodeSpecificMinSummary> entry : chainNodeContainer.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.COLUMN_FAMILY_NAME.getBytes()
                    , traceLevelId.getBytes(), entry.getValue().toString().getBytes());
            puts.add(put);
        }

        HBaseUtil.batchSaveMinSummaryResult(puts);
    }
}
