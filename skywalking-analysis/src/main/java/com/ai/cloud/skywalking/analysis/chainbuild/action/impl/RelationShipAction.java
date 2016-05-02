package com.ai.cloud.skywalking.analysis.chainbuild.action.impl;

import com.ai.cloud.skywalking.analysis.chainbuild.action.ISummaryAction;
import com.ai.cloud.skywalking.analysis.chainbuild.entity.CallChainTree;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.po.SpecificTimeCallTreeMergedChainIdContainer;
import com.google.gson.Gson;

import java.io.IOException;
import java.sql.SQLException;

public class RelationShipAction implements ISummaryAction {
    private CallChainTree chainTree;
    private SpecificTimeCallTreeMergedChainIdContainer container;
    public RelationShipAction(String entryKey) throws IOException {
         chainTree = CallChainTree.load(entryKey);
         container = new SpecificTimeCallTreeMergedChainIdContainer(chainTree.getTreeToken());
    }

    @Override
    public void doAction(String summaryData) throws IOException {
        ChainInfo chainInfo = new Gson().fromJson(summaryData, ChainInfo.class);
        container.addMergedChainIfNotContain(chainInfo);
    }

    @Override
    public void doSave() throws InterruptedException, SQLException, IOException {
        container.saveToHBase();
    }
}
