package com.ai.cloud.skywalking.alarm.model;

public class AlarmMessage {
	private String traceid;
	
	private String exceptionMsg;

	public AlarmMessage(String traceid, String exceptionMsg) {
		super();
		this.traceid = traceid;
		this.exceptionMsg = exceptionMsg;
	}

	public String getTraceid() {
		return traceid;
	}

	public String getExceptionMsg() {
		return exceptionMsg;
	}
}
