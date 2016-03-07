package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.TokenGenerator;

import java.io.IOException;
import java.util.*;

public class CallChainTree {

    private String callEntrance;

    private String treeToken;
    //合并之后的节点
    // key :  trace level Id
    private Map<String, CallChainTreeNode> nodes;

    public CallChainTree(String callEntrance) {
        nodes = new HashMap<String, CallChainTreeNode>();
        this.callEntrance = callEntrance;
        this.treeToken = TokenGenerator.generateTreeToken(callEntrance);
    }

    public static CallChainTree load(String callEntrance) throws IOException {
        CallChainTree chain = new CallChainTree(callEntrance);
        return chain;
    }

    public void summary(ChainInfo chainInfo) throws IOException {
        for (ChainNode node : chainInfo.getNodes()) {
            CallChainTreeNode callChainTreeNode = nodes.get(node.getTraceLevelId());
            if (callChainTreeNode == null) {
                callChainTreeNode = new CallChainTreeNode(node);
                nodes.put(node.getTraceLevelId() + "@" + node.getViewPoint(), callChainTreeNode);
            }
            callChainTreeNode.summary(treeToken, node);
        }
    }

    public void saveToHbase() throws IOException, InterruptedException {
        for (CallChainTreeNode entry : nodes.values()) {
            entry.saveSummaryResultToHBase();
        }
    }

    public String getTreeToken() {
        return treeToken;
    }
}
