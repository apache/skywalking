package com.ai.cloud.skywalking.analysis.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CategorizedChainInfo {
    private String chainToken;
    private String chainStr;

    private List<String> children_Token;

    public CategorizedChainInfo(ChainInfo chainInfo) {
        chainToken = chainInfo.getChainToken();

        StringBuilder stringBuilder = new StringBuilder();
        boolean flag = false;
        for (ChainNode chainNode : chainInfo.getNodes()) {
            if (flag) {
                stringBuilder.append(";");
            }
            stringBuilder.append(chainNode.getTraceLevelId() + "-" + chainNode.getViewPoint());
            flag = true;
        }

        chainStr = stringBuilder.toString();
        children_Token = new ArrayList<String>();
    }

    public String getChainStr() {
        return chainStr;
    }

    public boolean isContained(UncategorizeChainInfo uncategorizeChainInfo) {
        Pattern pattern = Pattern.compile(uncategorizeChainInfo.getNodeRegEx());
        return pattern.matcher(getChainStr()).find();
    }

    public void add(UncategorizeChainInfo uncategorizeChainInfo) {
        children_Token.add(uncategorizeChainInfo.getChainToken());
    }
}
