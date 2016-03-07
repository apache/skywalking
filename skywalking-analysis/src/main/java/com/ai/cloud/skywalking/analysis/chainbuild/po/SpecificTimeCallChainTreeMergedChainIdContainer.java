package com.ai.cloud.skywalking.analysis.chainbuild.po;

import com.ai.cloud.skywalking.analysis.chainbuild.util.HBaseUtil;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.google.gson.Gson;
import org.apache.hadoop.hbase.client.Put;

import java.io.IOException;
import java.util.*;

public class SpecificTimeCallChainTreeMergedChainIdContainer {

    private String treeToken;

    private Map<String, List<String>> hasBeenMergedChainIds;

    // 本次Reduce合并过的调用链
    private Map<String, ChainInfo> combineChains;

    public SpecificTimeCallChainTreeMergedChainIdContainer(String treeToken) {
        this.treeToken = treeToken;
        hasBeenMergedChainIds = new HashMap<String, List<String>>();
        combineChains = new HashMap<String, ChainInfo>();
    }

    public void addMergedChainIfNotContain(ChainInfo chainInfo) throws IOException {
        String key = generateKey(chainInfo.getStartDate());
        List<String> cIds = hasBeenMergedChainIds.get(key);
        if (cIds == null) {
            cIds = HBaseUtil.loadHasBeenMergeChainIds(key);
            hasBeenMergedChainIds.put(key, cIds);
        }

        if (!cIds.contains(chainInfo.getCID())) {
            cIds.add(chainInfo.getCID());
            combineChains.put(chainInfo.getCID(), chainInfo);
        }
    }


    private String generateKey(long date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(date));
        return treeToken + "@" + calendar.get(Calendar.YEAR) + "-" + calendar.get(Calendar.MONTH);
    }

    public void saveToHBase() throws IOException, InterruptedException {
        List<Put> chainInfoPuts = new ArrayList<Put>();
        for (Map.Entry<String, ChainInfo> entry : combineChains.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().saveToHBase(put);
            chainInfoPuts.add(put);
        }
        HBaseUtil.batchSaveChainInfo(chainInfoPuts);

        List<Put> chainIdPuts = new ArrayList<Put>();
        for (Map.Entry<String, List<String>> entry : hasBeenMergedChainIds.entrySet()) {
            Put chainIdPut = new Put(entry.getKey().getBytes());
            chainIdPut.addColumn(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.COLUMN_FAMILY_NAME.getBytes()
                    , "HAS_BEEN_MERGED_CHAIN_ID".getBytes(), new Gson().toJson(entry.getValue()).getBytes());
            chainIdPuts.add(chainIdPut);
        }

        HBaseUtil.batchSaveHasBeenMergedCID(chainIdPuts);
    }
}
