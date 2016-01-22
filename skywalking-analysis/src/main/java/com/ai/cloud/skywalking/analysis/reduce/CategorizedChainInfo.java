package com.ai.cloud.skywalking.analysis.reduce;

import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

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
            stringBuilder.append((chainNode.getTraceLevelId() + "-" + chainNode.getNodeToken()));
            flag = true;
        }

        chainStr = stringBuilder.toString();
        children_Token = new ArrayList<String>();
    }

    public CategorizedChainInfo(String value) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(value);
        chainToken = jsonObject.get("chainToken").getAsString();
        chainStr = jsonObject.get("chainStr").getAsString();
        children_Token = new Gson().fromJson(jsonObject.get("children_Token"),
                new TypeToken<List<String>>() {
                }.getType());
    }

    public String getChainStr() {
        return chainStr;
    }

    public boolean isContained(UncategorizeChainInfo uncategorizeChainInfo) {
        Pattern pattern = Pattern.compile(uncategorizeChainInfo.getNodeRegEx());
        return pattern.matcher(getChainStr()).find();
    }

    public boolean isAlreadyContained(UncategorizeChainInfo uncategorizeChainInfo) {
        return children_Token.contains(uncategorizeChainInfo.getChainToken());
    }

    public void add(UncategorizeChainInfo uncategorizeChainInfo) {
        children_Token.add(uncategorizeChainInfo.getChainToken());
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
