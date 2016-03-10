package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.HBaseUtil;
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
    private Map<String, ChainNodeSpecificMinSummary> chainNodeContainer;

    public CallChainTreeNode(ChainNode node) {
        this.traceLevelId = node.getTraceLevelId();
        chainNodeContainer = new HashMap<String, ChainNodeSpecificMinSummary>();
        this.viewPointId = node.getViewPoint();
    }

    public void summary(String treeId, ChainNode node) throws IOException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(node.getStartDate()));
        /**
         * 按分钟维度进行汇总<br/>
         * chainNodeContainer以treeId和时间（精确到分钟）为key，value为当前时间范围内的所有分钟的汇总数据
         */
        String keyOfMinSummaryTable = generateKeyOfMinSummaryTable(treeId, calendar);
        ChainNodeSpecificMinSummary minSummary = chainNodeContainer.get(keyOfMinSummaryTable);
        if (minSummary == null) {
            minSummary = HBaseUtil.loadSpecificMinSummary(keyOfMinSummaryTable, getTreeNodeId());
            chainNodeContainer.put(keyOfMinSummaryTable, minSummary);
        }

        minSummary.summary(String.valueOf(calendar.get(Calendar.MINUTE)), node);
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
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainNodeSpecificMinSummary> entry : chainNodeContainer.entrySet()) {
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
