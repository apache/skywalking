package com.ai.cloud.skywalking.analysis.util;

import com.ai.cloud.skywalking.analysis.model.ChainInfo;

public class ChainInfoUtil {
    private ChainInfoUtil() {
        // Non
    }

    public static ChainInfo generateUncategorizedChainInfo() {
        ChainInfo chainInfo = new ChainInfo();
        chainInfo.setChainToken("UNCATEGORIZED");
        chainInfo.setUserId("-1");
        return chainInfo;
    }
}
