package com.ai.cloud.skywalking.analysis.model;

public class UncategorizeChainInfo {
    private String chainToken;
    private String nodeRegEx;

    public UncategorizeChainInfo(ChainInfo chainInfo) {
        this.chainToken = chainInfo.getChainToken();
        StringBuilder stringBuilder = new StringBuilder();
        boolean flag = false;
        for (ChainNode node : chainInfo.getNodes()) {
            if (flag) {
                stringBuilder.append("*");
            }
            stringBuilder.append(node.getTraceLevelId() + "-" + node.getViewPoint());
            flag = true;
        }

        nodeRegEx = stringBuilder.toString();
    }

    public String getChainToken() {
        return chainToken;
    }

    public void setChainToken(String chainToken) {
        this.chainToken = chainToken;
    }

    public String getNodeRegEx() {
        return nodeRegEx;
    }

    public void setNodeRegEx(String nodeRegEx) {
        this.nodeRegEx = nodeRegEx;
    }
}
