package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.ai.cloud.skywalking.analysis.chainbuild.DBCallChainInfoDao;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.google.gson.Gson;

public class CallChainDetailForMysql {
    private String chainToken;
    private String treeToken;
    private Map<String, ChainNode> chainNodeMap = new HashMap<String, ChainNode>();
    private String userId;

    public CallChainDetailForMysql(ChainInfo chainInfo, String treeToken) {
        chainToken = chainInfo.getCID();
        for (ChainNode chainNode : chainInfo.getNodes()) {
            chainNodeMap.put(chainNode.getTraceLevelId(), chainNode);
        }
        userId = chainInfo.getUserId();
        this.treeToken = treeToken;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    public void saveToMysql() throws SQLException {
        DBCallChainInfoDao.saveChainDetail(this);
    }

    public Collection<ChainNode> getChainNodes() {
        return chainNodeMap.values();
    }

    public String getUserId() {
        return userId;
    }

    public String getChainToken() {
        return chainToken;
    }

    public String getTreeToken() {
        return treeToken;
    }
}
