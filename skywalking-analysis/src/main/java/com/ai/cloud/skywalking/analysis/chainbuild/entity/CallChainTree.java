package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.TokenGenerator;

public class CallChainTree {
    private String callEntrance;

    private String treeToken;
    
    /**
     * 命名规则：levelId + @ + viewPoint
     * 存放各级的各个viewpoint节点的统计数值
     */
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
        	CallChainTreeNode newCallChainTreeNode = new CallChainTreeNode(node);
            CallChainTreeNode callChainTreeNode = nodes.get(newCallChainTreeNode.getTreeNodeId());
            if (callChainTreeNode == null) {
                callChainTreeNode = newCallChainTreeNode;
                nodes.put(newCallChainTreeNode.getTreeNodeId(), callChainTreeNode);
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
