package com.ai.cloud.skywalking.web.entity;

import com.ai.cloud.skywalking.web.dto.AnlyResult;
import com.ai.cloud.skywalking.web.dto.CallChainNode;
import com.ai.cloud.skywalking.web.util.ViewPointBeautiUtil;

import java.util.*;

/**
 * Created by xin on 16-4-6.
 */
public class CallChainTree {
    private String treeId;
    private String entranceViewpoint;
    private List<CallChainNode> nodes;
    private AnlyResult entranceAnlyResult;

    public CallChainTree(String treeId) {
        this.treeId = treeId;
        nodes = new ArrayList<CallChainNode>();
    }

    public void setEntranceAnlyResult(AnlyResult entranceAnlyResult) {
        this.entranceAnlyResult = entranceAnlyResult;
    }

    public void addHitNodes(Map<String, String> nodes) {
        for (Map.Entry<String, String> entry : nodes.entrySet()) {
            CallChainNode callChainNode = new CallChainNode(entry.getKey(), entry.getValue(), false);
            this.nodes.add(callChainNode);
        }
    }

    public void beautiViewPointString(String searchKey) {
        if (entranceViewpoint.length() > 100) {
            entranceViewpoint = ViewPointBeautiUtil.beautifulViewPoint(entranceViewpoint, searchKey);
            //
        }

        for (CallChainNode chainNode : nodes) {
            //高亮
            chainNode.beautiViewPointString(searchKey);
        }
    }

    public void sortNodes() {
        Collections.sort(nodes, new Comparator<CallChainNode>() {
            @Override
            public int compare(CallChainNode o1, CallChainNode o2) {
                return o1.getTraceLevelId().compareTo(o2.getTraceLevelId());
            }
        });
    }

    public void addGuessNodesAndRemoveDuplicate(List<CallChainNode> nodes) {
        for (CallChainNode node : nodes) {
            if (!this.nodes.contains(node)) {
                this.nodes.add(node);
            }
        }
    }

    public void setEntranceViewpoint(String entranceViewpoint) {
        this.entranceViewpoint = entranceViewpoint;
    }

    public AnlyResult getEntranceAnlyResult() {
        return entranceAnlyResult;
    }

    public List<CallChainNode> getNodes() {
        return nodes;
    }

    public String getTreeId() {
        return treeId;
    }

    public String getEntranceViewpoint() {
        return entranceViewpoint;
    }

    public void setTreeId(String treeId) {
        this.treeId = treeId;
    }

    public void setNodes(List<CallChainNode> nodes) {
        this.nodes = nodes;
    }
}
