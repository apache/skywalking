package com.ai.cloud.skywalking.analysis.chainbuild.action.impl;

import com.ai.cloud.skywalking.analysis.chainbuild.action.ISummaryAction;
import com.ai.cloud.skywalking.analysis.chainbuild.entity.CallChainTree;
import com.ai.cloud.skywalking.analysis.chainbuild.entity.CallChainTreeNode;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.po.SummaryType;
import com.google.gson.Gson;

import java.io.IOException;
import java.sql.SQLException;

public class DateDetailSummaryAction implements ISummaryAction {
    private CallChainTree callChainTree;
    private String summaryDate;
    private SummaryType summaryType;

    public DateDetailSummaryAction(String entryKey, String summaryDate) throws IOException {
        callChainTree = CallChainTree.load(entryKey);
        this.summaryDate = summaryDate;
    }

    @Override
    public void doAction(String summaryData) throws IOException {
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
        callChainTree.saveToHBase(summaryType);
    }

    public void setSummaryType(SummaryType summaryType) {
        this.summaryType = summaryType;
    }
}
