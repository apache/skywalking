package com.ai.cloud.skywalking.reciever.storage.chain.alarm;

import com.ai.cloud.skywalking.protocol.Span;

public class ExecuteTimeChecker extends AbstractSpanChecker {

	@Override
	public void check(Span span) {
		long cost = span.getCost();

		if (cost > 500 && cost < 3000) {
			/**
			 * Issue #43 <br/>
			 * 单埋点调用时间超过500ms的进行预警
			 */
			saveAlarmMessage(generateWarningAlarmKey(span), span.getTraceId(), span.getViewPointId() + " cost " + cost + " ms.");
		}
		if (cost >= 3000) {
			/**
			 * Issue #43 <br/>
			 * 单埋点调用时间超过3S的进行告警
			 */
			saveAlarmMessage(generatePossibleErrorAlarmKey(span), span.getTraceId(), span.getViewPointId() + " cost " + cost + " ms.");
		}
	}

	private String generateWarningAlarmKey(Span span) {
		return span.getUserId() + "-" + span.getApplicationId() + "-"
				+ (System.currentTimeMillis() / (10000 * 6)) + "-ExecuteTime-Warning";
	}
	
	private String generatePossibleErrorAlarmKey(Span span) {
		return span.getUserId() + "-" + span.getApplicationId() + "-"
				+ (System.currentTimeMillis() / (10000 * 6)) + "-ExecuteTime-PossibleError";
	}
}
