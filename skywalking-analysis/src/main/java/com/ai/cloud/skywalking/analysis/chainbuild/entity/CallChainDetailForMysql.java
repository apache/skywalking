package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.DBCallChainInfoDao;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.google.gson.Gson;
import org.apache.hadoop.hbase.client.Put;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CallChainDetailForMysql {
    private String chainToken;
    private Map<String, ChainNode> chainNodeMap = new HashMap<String, ChainNode>();
    private String userId;

    public CallChainDetailForMysql(ChainInfo chainInfo) {
        chainToken = chainInfo.getCID();
        for (ChainNode chainNode : chainInfo.getNodes()) {
            chainNodeMap.put(chainNode.getTraceLevelId(), chainNode);
        }
        userId = chainInfo.getUserId();
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
}
