package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.HBaseUtil;
import com.google.gson.Gson;
import org.apache.hadoop.hbase.client.Put;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallChainTree {

    private String callEntrance;

    private String treeId;

    //存放已经合并过的调用链ID
    private List<String> hasBeenMergedChainIds;

    // 本次Reduce合并过的调用链
    private Map<String, ChainInfo> combineChains;

    //合并之后的节点
    // key :  trace level Id
    private Map<String, CallChainTreeNode> nodes;

    public CallChainTree(String callEntrance) {
        hasBeenMergedChainIds = new ArrayList<String>();
        combineChains = new HashMap<String, ChainInfo>();
        nodes = new HashMap<String, CallChainTreeNode>();
        this.callEntrance = callEntrance;
    }

    public static CallChainTree load(String callEntrance) throws IOException {
        CallChainTree chain = HBaseUtil.loadCallChainTree(callEntrance);
        chain.hasBeenMergedChainIds.addAll(HBaseUtil.loadHasBeenMergeChainIds(callEntrance));
        if (chain == null) {
            chain = new CallChainTree(callEntrance);
        }
        return chain;
    }

    public void processMerge(ChainInfo chainInfo) {
        if (hasBeenMergedChainIds.contains(chainInfo.getCID())) {
            return;
        }

        for (ChainNode node : chainInfo.getNodes()) {
            CallChainTreeNode callChainTreeNode = nodes.get(node.getTraceLevelId());
            if (callChainTreeNode != null) {
                callChainTreeNode.mergeIfNess(node);
            } else {
                nodes.put(node.getTraceLevelId(), new CallChainTreeNode(node));
            }
        }

        hasBeenMergedChainIds.add(chainInfo.getCID());
        combineChains.put(chainInfo.getCID(), chainInfo);
    }

    public void summary(ChainInfo chainInfo) throws IOException {
        for (ChainNode node : chainInfo.getNodes()) {
            CallChainTreeNode callChainTreeNode = nodes.get(node.getTraceLevelId());
            callChainTreeNode.summary(treeId, node);
        }
    }

    public void saveToHbase() throws IOException {
        List<Put> chainInfoPuts = new ArrayList<Put>();
        for (Map.Entry<String, ChainInfo> entry : combineChains.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().saveToHBase(put);
            chainInfoPuts.add(put);
        }

        HBaseUtil.saveCallChainTree(this);

        //save summary value
        for (CallChainTreeNode entry : nodes.values()) {
            entry.saveSummaryResultToHBase();
        }
    }

    public String getCallEntrance() {
        return callEntrance;
    }

    public void addMergedChainNode(CallChainTreeNode chainNode) {
        nodes.put(chainNode.getTraceLevelId(), chainNode);
    }

    public Map<String, CallChainTreeNode> getNodes() {
        return nodes;
    }

    public String getHasBeenMergedChainIds() {
        return new Gson().toJson(hasBeenMergedChainIds);
    }
}
