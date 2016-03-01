package com.ai.cloud.skywalking.analysis.chainbuild.entity;

public class VisualTraceSpanNode extends TraceSpanNode {

	protected VisualTraceSpanNode(TraceSpanNode parent, TraceSpanNode sub,
			TraceSpanNode prev, TraceSpanNode next, String parentLevelId,
			int levelId) {
		super(parent, sub, prev, next, parentLevelId, levelId);
	}


}
