package com.ai.cloud.skywalking.analysis.reduce;

import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

public class UncategorizeChainInfo {
    @Expose
    private String chainToken;
    @Expose
    private String nodeRegEx;

    private ChainInfo chainInfo;

    public UncategorizeChainInfo() {
    }

    public UncategorizeChainInfo(ChainInfo chainInfo) {
        this.chainToken = chainInfo.getChainToken();
        StringBuilder stringBuilder = new StringBuilder();
        boolean flag = false;
        for (ChainNode node : chainInfo.getNodes()) {
            if (flag) {
                stringBuilder.append(";*");
            }
            stringBuilder.append((node.getTraceLevelId() + "-" + node.getNodeToken()));
            flag = true;
        }

        nodeRegEx = stringBuilder.toString();

        this.chainInfo = chainInfo;
    }

    public String getChainToken() {
        return chainToken;
    }

    public String getNodeRegEx() {
        return nodeRegEx;
    }

    public ChainInfo getChainInfo() {
        return chainInfo;
    }

    @Override
    public String toString() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.excludeFieldsWithoutExposeAnnotation();
        return gsonBuilder.create().toJson(this);
    }
}
