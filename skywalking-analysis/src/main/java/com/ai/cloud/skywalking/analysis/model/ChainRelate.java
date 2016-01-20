package com.ai.cloud.skywalking.analysis.model;

import java.util.*;

public class ChainRelate {
    private Map<String, CategorizedChainInfo> categorizedChainInfoList = new HashMap<String, CategorizedChainInfo>();
    private List<UncategorizeChainInfo> uncategorizeChainInfoList = new ArrayList<UncategorizeChainInfo>();

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
        for (Map.Entry<String, CategorizedChainInfo> entry : categorizedChainInfoList.entrySet()) {
            if (entry.getValue().isContained(child)) {
                entry.getValue().add(child);
                isContained = true;
            }
        }

        if (!isContained) {
            uncategorizeChainInfoList.add(child);
        }
    }

    private CategorizedChainInfo addCategorizedChain(ChainInfo chainInfo) {
        if (!categorizedChainInfoList.containsKey(chainInfo.getChainToken())) {
            categorizedChainInfoList.put(chainInfo.getChainToken(),
                    new CategorizedChainInfo(chainInfo));
        }
        return categorizedChainInfoList.get(chainInfo.getChainToken());
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

    public void save() {

    }
}
