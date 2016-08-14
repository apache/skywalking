package com.a.eye.skywalking.analysis.chainbuild.action.impl;

import java.io.IOException;
import java.sql.SQLException;

import com.a.eye.skywalking.analysis.chainbuild.entity.CallChainTree;
import com.a.eye.skywalking.analysis.chainbuild.action.IStatisticsAction;
import com.a.eye.skywalking.analysis.chainbuild.po.ChainInfo;
import com.a.eye.skywalking.analysis.chainbuild.po.SpecificTimeCallChainTreeContainer;
import com.google.gson.Gson;

public class CallChainRelationshipAction implements IStatisticsAction {
    private CallChainTree                      chainTree;
    private SpecificTimeCallChainTreeContainer container;
    public CallChainRelationshipAction(String entryKey) throws IOException {
         chainTree = CallChainTree.create(entryKey);
         container = new SpecificTimeCallChainTreeContainer(chainTree.getTreeToken());
    }

    @Override
    public void doAction(String summaryData) throws IOException {
        ChainInfo chainInfo = new Gson().fromJson(summaryData, ChainInfo.class);
        container.addChainIfNew(chainInfo);
    }

    @Override
    public void doSave() throws InterruptedException, SQLException, IOException {
        container.saveToHBase();
    }
}
