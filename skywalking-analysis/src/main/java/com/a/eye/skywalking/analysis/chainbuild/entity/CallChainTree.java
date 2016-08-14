package com.a.eye.skywalking.analysis.chainbuild.entity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.a.eye.skywalking.analysis.chainbuild.po.SummaryType;
import com.a.eye.skywalking.analysis.chainbuild.util.TokenGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CallChainTree {
    private Logger logger = LogManager.getLogger(CallChainTree.class);

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
        logger.info("CallEntrance:[{}] == TreeToken[{}]",callEntrance, treeToken);
    }

    public static CallChainTree create(String callEntrance) throws IOException {
        CallChainTree chain = new CallChainTree(callEntrance);
        return chain;
    }

    public void saveToHBase(SummaryType summaryType) throws IOException, InterruptedException {
        for (CallChainTreeNode entry : nodes.values()) {
            entry.saveSummaryResultToHBase(summaryType);
        }
    }

    public String getTreeToken() {
        return treeToken;
    }

    public Map<String, CallChainTreeNode> getNodes() {
        return nodes;
    }
}
