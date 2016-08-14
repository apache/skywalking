package com.a.eye.skywalking.analysis.chainbuild.action.impl;

import java.io.IOException;
import java.sql.SQLException;

import com.a.eye.skywalking.analysis.chainbuild.action.IStatisticsAction;
import com.a.eye.skywalking.analysis.chainbuild.entity.CallChainTree;
import com.a.eye.skywalking.analysis.chainbuild.entity.CallChainTreeNode;
import com.a.eye.skywalking.analysis.chainbuild.entity.ChainNodeSpecificTimeWindowSummaryValue;
import com.a.eye.skywalking.analysis.chainbuild.po.ChainNode;
import com.a.eye.skywalking.analysis.chainbuild.po.SummaryType;
import com.google.gson.Gson;

public class NumberOfCalledStatisticsAction implements IStatisticsAction {
    private CallChainTree callChainTree;
    private String        summaryDate;
    private SummaryType   summaryType;
    
    /**
     * 统计任务的主要存储值
     * 
     * TODO：配置本次修改
     */
    private ChainNodeSpecificTimeWindowSummaryValue summaryValue = new ChainNodeSpecificTimeWindowSummaryValue();

    public NumberOfCalledStatisticsAction(String entryKey, String summaryDate) throws IOException {
        callChainTree = CallChainTree.create(entryKey);
        this.summaryDate = summaryDate;
    }

    @Override
    public void doAction(String summaryData) throws IOException {
    	/**
    	 * TODO:根据MAP的优化，大幅度减少对象逻辑复杂度
    	 * 
    	 * 每次传递来的数据，根据NodeStatus的值在内存中快速累加到ChainNodeSpecificTimeWindowSummaryValue中
    	 * 
    	 * ChainNodeSpecificTimeWindowSummaryValue执行以下逻辑：
    	 * 1.totalCall默认+1
    	 * 2.totalCostTime根据cost累加
    	 * 3.correctNumber和humanInterruptionNumber根据状态累加
    	 * 
    	 * 注意：
    	 * 1.减少对象创建和嵌套
    	 * 2.此过程中不要操作任何hbase数据
    	 */
    	
        ChainNode chainNode = new Gson().fromJson(summaryData, ChainNode.class);
        CallChainTreeNode newCallChainTreeNode = new CallChainTreeNode(chainNode);
        CallChainTreeNode callChainTreeNode = callChainTree.getNodes().get(newCallChainTreeNode.getTreeNodeId());
        if (callChainTreeNode == null) {
            callChainTreeNode = newCallChainTreeNode;
            callChainTree.getNodes().put(newCallChainTreeNode.getTreeNodeId(), callChainTreeNode);
        }
        callChainTreeNode.summary(callChainTree.getTreeToken(), chainNode, summaryType, summaryDate);
    }

    @Override
    public void doSave() throws InterruptedException, SQLException, IOException {
    	/**
    	 * TODO:新的写入逻辑，快速完成
    	 * 
    	 * 根据Config.AnalysisServer.IS_ACCUMULATE_MODE决定，在需要时，精确读取指定rowkey的指定列值。
    	 * 和ChainNodeSpecificTimeWindowSummaryValue值进行简单相加，然后汇总写入。
    	 * 
    	 * 注意：
    	 * 1.减少对象创建和嵌套
    	 * 2。确保任务的告诉高效完成
    	 */
        callChainTree.saveToHBase(summaryType);
    }

    public void setSummaryType(SummaryType summaryType) {
        this.summaryType = summaryType;
    }
}
