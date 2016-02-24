package com.ai.cloud.skywalking.analysis.chain2summary;

import com.ai.cloud.skywalking.analysis.categorize2chain.util.HBaseUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChainRelationship4Search {

    // key: 正常链路ID value: 正常链路ID
    // key: 异常链路ID value: 正常链路ID
    // key: 未分类链路ID value: 未分类链路ID
    private Map<String, String> chainRelationshipMap = new HashMap<String, String>();

    public static ChainRelationship4Search load(String rowkey) throws IOException {
        ChainRelationship4Search chainRelationship4Search = HBaseUtil.queryChainRelationship(rowkey);
        return chainRelationship4Search;
    }

    public void addRelationship(String cid) {
        chainRelationshipMap.put(cid, cid);
    }

    public void addRelationship(String normalCID, String abnormalCID) {
        chainRelationshipMap.put(normalCID, abnormalCID);
    }

    public String searchRelationship(String cid) {
        return chainRelationshipMap.get(cid);
    }
}
