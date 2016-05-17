package com.ai.cloud.skywalking.analysis.chainbuild.filter.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.analysis.chainbuild.SpanEntry;
import com.ai.cloud.skywalking.analysis.chainbuild.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;

public class JDBCBusinessKeyHandleFilter extends SpanNodeProcessFilter {
    private Logger logger = LogManager.getLogger(JDBCBusinessKeyHandleFilter.class);

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {
        String businessKey = spanEntry.getBusinessKey();
        if (businessKey != null) {
            int index = businessKey.lastIndexOf(":");
            if (index != -1) {
                businessKey = businessKey.substring(index + 1).trim();
                String key = businessKey.toUpperCase();
                if (key.startsWith("SELECT")) {
                    businessKey = subBusinessKey(businessKey, key, "WHERE");
                } else if (key.startsWith("UPDATE")) {
                    businessKey = subBusinessKey(businessKey, key, "SET");
                } else if (key.startsWith("DELETE")) {
                    businessKey = subBusinessKey(businessKey, key, "WHERE");
                } else if (key.startsWith("INSERT")) {
                    businessKey = subBusinessKey(businessKey, key, "VALUES");
                }
            }
        }
        spanEntry.setBusinessKey(businessKey);
        this.doNext(spanEntry, node, costMap);
    }

    private String subBusinessKey(String businessKey, String key, String keyWord) {
        int whereIndex = key.indexOf(keyWord);
        if (whereIndex != -1) {
            businessKey = businessKey.substring(0, whereIndex - 1).trim();
        }
        return businessKey;
    }
}
