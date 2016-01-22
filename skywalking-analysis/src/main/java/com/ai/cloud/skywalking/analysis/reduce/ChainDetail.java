package com.ai.cloud.skywalking.analysis.reduce;

import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.dao.CallChainInfoDao;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.google.gson.Gson;
import org.apache.hadoop.hbase.client.Put;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ChainDetail {
    private String chainToken;
    private Map<String, ChainNode> chainNodeMap = new HashMap<String, ChainNode>();
    private String userId;

    public ChainDetail(ChainInfo chainInfo) {
        chainToken = chainInfo.getChainToken();
        for (ChainNode chainNode : chainInfo.getNodes()) {
            chainNodeMap.put(chainNode.getTraceLevelId(), chainNode);
        }
        userId = chainInfo.getUserId();
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    public void save(Put put) throws SQLException {
        for (Map.Entry<String, ChainNode> entry : chainNodeMap.entrySet()){
            put.addColumn(Config.HBase.TRACE_DETAIL_FAMILY_COLUMN.getBytes(),entry.getKey().getBytes(),
                    entry.getValue().toString().getBytes());
        }

        CallChainInfoDao.saveChainDetail(this);
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
