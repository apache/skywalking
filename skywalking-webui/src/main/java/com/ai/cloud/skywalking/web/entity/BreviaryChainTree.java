package com.ai.cloud.skywalking.web.entity;

import com.ai.cloud.skywalking.web.dto.AnlyResult;
import com.ai.cloud.skywalking.web.util.ViewPointBeautiUtil;

import java.util.*;

/**
 * Created by xin on 16-4-6.
 */
public class BreviaryChainTree {
    private String treeId;
    private String entranceViewpoint;
    private List<BreviaryChainNode> nodes;
    private AnlyResult entranceAnlyResult;

    public BreviaryChainTree(String treeId) {
        this.treeId = treeId;
        nodes = new ArrayList<BreviaryChainNode>();
    }

    public void setEntranceAnlyResult(AnlyResult entranceAnlyResult) {
        this.entranceAnlyResult = entranceAnlyResult;
    }

    public void addHitNodes(Map<String, String> nodes) {
        for (Map.Entry<String, String> entry : nodes.entrySet()) {
            BreviaryChainNode breviaryChainNode = new BreviaryChainNode(entry.getKey(), entry.getValue(), false);
            this.nodes.add(breviaryChainNode);
        }
    }

    public void beautiViewPointString(String searchKey) {
        if (entranceViewpoint.length() > 100) {
            entranceViewpoint = ViewPointBeautiUtil.beautifulViewPoint(entranceViewpoint, searchKey);
            //
        }

        for (BreviaryChainNode chainNode : nodes) {
            //高亮
            chainNode.beautiViewPointString(searchKey);
        }
    }

    public void sortNodes() {
        Collections.sort(nodes, new Comparator<BreviaryChainNode>() {
            @Override
            public int compare(BreviaryChainNode o1, BreviaryChainNode o2) {
                return o1.getTraceLevelId().compareTo(o2.getTraceLevelId());
            }
        });
    }

    public void addGuessNodesAndRemoveDuplicate(List<BreviaryChainNode> nodes) {
        for (BreviaryChainNode node : nodes) {
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

    public List<BreviaryChainNode> getNodes() {
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

    public void setNodes(List<BreviaryChainNode> nodes) {
        this.nodes = nodes;
    }
}
