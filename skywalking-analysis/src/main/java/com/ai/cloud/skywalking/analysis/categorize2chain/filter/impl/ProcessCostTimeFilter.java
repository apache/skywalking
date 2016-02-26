package com.ai.cloud.skywalking.analysis.categorize2chain.filter.impl;

import com.ai.cloud.skywalking.analysis.categorize2chain.SpanEntry;
import com.ai.cloud.skywalking.analysis.categorize2chain.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.categorize2chain.po.ChainNode;
import com.ai.cloud.skywalking.analysis.categorize2chain.util.SubLevelSpanCostCounter;

public class ProcessCostTimeFilter extends SpanNodeProcessFilter {
	@Override
	public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {
		node.setCost(spanEntry.getCost());
		
		this.saveCostAsSubNodeCost(spanEntry, node, costMap);
		this.computeChainNodeCost(costMap, node);

		this.doNext(spanEntry, node, costMap);
	}

	private void saveCostAsSubNodeCost(SpanEntry spanEntry, ChainNode node,
			SubLevelSpanCostCounter costMap) {
		long subNodeCost = spanEntry.getCost();
		if (costMap.exists(spanEntry.getParentLevelId())) {
			subNodeCost += costMap.get(spanEntry.getParentLevelId());
		}

		costMap.put(spanEntry.getParentLevelId(), subNodeCost);
	}

	private void computeChainNodeCost(SubLevelSpanCostCounter costMap, ChainNode node) {
		String levelId = node.getParentLevelId();
		if (levelId != null && levelId.length() > 0) {
			levelId += ".";
		}
		levelId += node.getLevelId() + "";

		if (costMap.exists(levelId)) {
			node.setCost(node.getCost() - costMap.get(levelId));
		}
	}
}
