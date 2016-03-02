package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import java.util.List;

import com.ai.cloud.skywalking.analysis.chainbuild.util.StringUtil;

public class VisualTraceSpanNode extends TraceSpanNode {

	protected VisualTraceSpanNode(TraceSpanNode parent, TraceSpanNode sub,
			TraceSpanNode prev, TraceSpanNode next, String parentLevelId,
			int levelId, List<TraceSpanNode> spanContainer) {
		super(parent, sub, prev, next, parentLevelId, levelId, spanContainer);
		
		/**set visual node token.<br/>
		 * for example: <br/>
		 *    VisualNode[0.0]<br/>
		 *    VisualNode[0.0.1]<br/>
		 *    etc.<br/>
		 */
		nodeRefToken = "VisualNode[" + (StringUtil.isBlank(parentLevelId) ? "": nodeRefToken + ".") + levelId + "]";
	}


}
