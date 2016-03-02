package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import java.util.List;

public class VisualTraceSpanNode extends TraceSpanNode {

	protected VisualTraceSpanNode(TraceSpanNode parent, TraceSpanNode sub,
			TraceSpanNode prev, TraceSpanNode next, String parentLevelId,
			int levelId, List<TraceSpanNode> spanContainer) {
		super(parent, sub, prev, next, parentLevelId, levelId, spanContainer);
		
		//TODO: to set nodeToken
	}


}
