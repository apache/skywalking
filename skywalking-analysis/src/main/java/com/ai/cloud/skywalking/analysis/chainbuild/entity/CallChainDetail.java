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

public class CallChainDetail {
    private boolean isNormal = true;
    private String chainToken;
    private Map<String, ChainNode> chainNodeMap = new HashMap<String, ChainNode>();
    private String userId;

    public CallChainDetail(ChainInfo chainInfo, boolean isNormal) {
        chainToken = chainInfo.getCID();
        for (ChainNode chainNode : chainInfo.getNodes()) {
            chainNodeMap.put(chainNode.getTraceLevelId(), chainNode);
        }
        userId = chainInfo.getUserId();

        this.isNormal = isNormal;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    public void save(Put put) throws SQLException {
        for (Map.Entry<String, ChainNode> entry : chainNodeMap.entrySet()){
            put.addColumn(HBaseTableMetaData.TABLE_CHAIN_DETAIL.COLUMN_FAMILY_NAME.getBytes(),entry.getKey().getBytes(),
                    entry.getValue().toString().getBytes());
        }
        if (isNormal) {
            DBCallChainInfoDao.saveChainDetail(this);
        }
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
