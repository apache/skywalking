package com.ai.cloud.skywalking.analysis.categorize2chain;

import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CategorizedChainInfo {
    private String cid;
    private String chainFullToken;

    private List<String> children_Token;

    public CategorizedChainInfo(ChainInfo chainInfo) {
    	cid = chainInfo.getCID();

        StringBuilder stringBuilder = new StringBuilder();
        boolean flag = false;
        for (ChainNode chainNode : chainInfo.getNodes()) {
            if (flag) {
                stringBuilder.append(";");
            }
            stringBuilder.append(chainNode.getNodeToken());
            flag = true;
        }

        chainFullToken = stringBuilder.toString();
        children_Token = new ArrayList<String>();
    }

    public CategorizedChainInfo(String value) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(value);
        cid = jsonObject.get("chainToken").getAsString();
        chainFullToken = jsonObject.get("chainFullToken").getAsString();
        children_Token = new Gson().fromJson(jsonObject.get("children_Token"),
                new TypeToken<List<String>>() {
                }.getType());
    }

    public String getChainFullToken() {
        return chainFullToken;
    }

    public boolean isContained(UncategorizeChainInfo uncategorizeChainInfo) {
        Pattern pattern = Pattern.compile(uncategorizeChainInfo.getNodeRegEx());
        return pattern.matcher(this.chainFullToken).find();
    }

    public boolean isAlreadyContained(UncategorizeChainInfo uncategorizeChainInfo) {
        return children_Token.contains(uncategorizeChainInfo.getCID());
    }

    public void add(UncategorizeChainInfo uncategorizeChainInfo) {
        children_Token.add(uncategorizeChainInfo.getCID());
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
