package com.ai.cloud.skywalking.web.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 16-4-6.
 */
public class CallChainTree {
    private String treeId;
    private List<CallChainTreeNode> nodes;

    private String entranceViewpoint;

    public CallChainTree(String treeId) {
        this.treeId = treeId;
        nodes = new ArrayList<CallChainTreeNode>();
    }

    public void addNode(String levelId, String viewpoint) {
        if ("0".equals(levelId)) {
            entranceViewpoint = viewpoint;
        }

        nodes.add(new CallChainTreeNode(levelId, viewpoint));
    }
}
