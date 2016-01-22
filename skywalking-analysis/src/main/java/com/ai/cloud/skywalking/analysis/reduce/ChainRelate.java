package com.ai.cloud.skywalking.analysis.reduce;

import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.Constants;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;
import com.google.gson.Gson;
import org.apache.hadoop.hbase.client.Put;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class ChainRelate {
    private String key;
    private Map<String, CategorizedChainInfo> categorizedChainInfoMap = new HashMap<String, CategorizedChainInfo>();
    private List<UncategorizeChainInfo> uncategorizeChainInfoList = new ArrayList<UncategorizeChainInfo>();
    private Map<String, ChainDetail> chainDetailMap = new HashMap<String, ChainDetail>();

    public ChainRelate(String key) {
        this.key = key;
    }

    private void categoryUncategorizedChainInfo(CategorizedChainInfo parentChains) {
        if (uncategorizeChainInfoList != null && uncategorizeChainInfoList.size() > 0) {
            Iterator<UncategorizeChainInfo> uncategorizeChainInfoIterator = uncategorizeChainInfoList.iterator();
            while (uncategorizeChainInfoIterator.hasNext()) {
                UncategorizeChainInfo uncategorizeChainInfo = uncategorizeChainInfoIterator.next();
                if (parentChains.isContained(uncategorizeChainInfo)) {
                    parentChains.add(uncategorizeChainInfo);
                    uncategorizeChainInfoIterator.remove();
                }
            }
        }
    }

    private void classifiedChains(UncategorizeChainInfo child) {
        boolean isContained = false;
        for (Map.Entry<String, CategorizedChainInfo> entry : categorizedChainInfoMap.entrySet()) {
            if (entry.getValue().isAlreadyContained(child)) {
                isContained = true;
            } else if (entry.getValue().isContained(child)) {
                entry.getValue().add(child);
                isContained = true;
            }
        }

        if (!isContained) {
            uncategorizeChainInfoList.add(child);
        }

    }

    private CategorizedChainInfo addCategorizedChain(ChainInfo chainInfo) {
        if (!categorizedChainInfoMap.containsKey(chainInfo.getChainToken())) {
            categorizedChainInfoMap.put(chainInfo.getChainToken(),
                    new CategorizedChainInfo(chainInfo));

            chainDetailMap.put(chainInfo.getChainToken(), new ChainDetail(chainInfo));
        }
        return categorizedChainInfoMap.get(chainInfo.getChainToken());
    }

    public void addRelate(ChainInfo chainInfo) {
        if (chainInfo.getChainStatus() == ChainInfo.ChainStatus.NORMAL) {
            CategorizedChainInfo categorizedChainInfo = addCategorizedChain(chainInfo);
            categoryUncategorizedChainInfo(categorizedChainInfo);
        } else {
            UncategorizeChainInfo uncategorizeChainInfo = new UncategorizeChainInfo(chainInfo);
            classifiedChains(uncategorizeChainInfo);
        }
    }

    public void save() throws SQLException {
        saveChainRelationShip();
        saveChainDetail();
    }

    private void saveChainDetail() throws SQLException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainDetail> entry : chainDetailMap.entrySet()) {
            Put put1 = new Put(entry.getKey().getBytes());
            puts.add(put1);
            entry.getValue().save(put1);
        }


        try {
            HBaseUtil.saveChainDetails(puts);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void saveChainRelationShip() {
        Put put = new Put(getKey().getBytes());

        put.addColumn(Config.HBase.CHAIN_RELATIONSHIP_COLUMN_FAMILY.getBytes(), Constants.UNCATEGORIZED_QUALIFIER_NAME.getBytes()
                , new Gson().toJson(getUncategorizeChainInfoList()).getBytes());

        for (Map.Entry<String, CategorizedChainInfo> entry : getCategorizedChainInfoMap().entrySet()) {
            put.addColumn(Config.HBase.CHAIN_RELATIONSHIP_COLUMN_FAMILY.getBytes(), entry.getKey().getBytes()
                    , entry.getValue().toString().getBytes());
        }

        try {
            HBaseUtil.saveChainRelate(put);
        } catch (IOException e) {
            //TODO
            e.printStackTrace();
        }
    }

    public void addCategorizeChain(String qualifierName, CategorizedChainInfo categorizedChainInfo) {
        categorizedChainInfoMap.put(qualifierName, categorizedChainInfo);
    }

    public String getKey() {
        return key;
    }

    public Map<String, CategorizedChainInfo> getCategorizedChainInfoMap() {
        return categorizedChainInfoMap;
    }

    public List<UncategorizeChainInfo> getUncategorizeChainInfoList() {
        return uncategorizeChainInfoList;
    }

    public void addUncategorizeChain(List<UncategorizeChainInfo> uncategorizeChainInfos) {
        uncategorizeChainInfoList.addAll(uncategorizeChainInfos);
    }
}
