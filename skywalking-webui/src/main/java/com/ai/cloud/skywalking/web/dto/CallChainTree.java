package com.ai.cloud.skywalking.web.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 16-4-25.
 */
public class CallChainTree {

    private String treeId;
    private List<CallChainTreeNode> callChainTreeNodeList;

    public CallChainTree() {
        callChainTreeNodeList = new ArrayList<CallChainTreeNode>();
    }

    public void addNode(CallChainTreeNode callChainTreeNode) {
        callChainTreeNodeList.add(callChainTreeNode);
    }

    public void setTreeId(String treeId) {
        this.treeId = treeId;
    }

    public List<CallChainTreeNode> getCallChainTreeNodeList() {
        return callChainTreeNodeList;
    }

    public void setCallChainTreeNodeList(List<CallChainTreeNode> callChainTreeNodeList) {
        this.callChainTreeNodeList = callChainTreeNodeList;
    }

    public void beautifulViewPointForShow() {
        for (CallChainTreeNode node : callChainTreeNodeList){
            node.beautifulViewPoint();
        }
    }
}
