package com.ai.cloud.skywalking.alarm.model;

import java.util.Date;

public class AlarmMessage {
	
	private String traceid;	
	private String exceptionMsg;
	private Date date;

	public AlarmMessage(String traceid, String exceptionMsg) {
		super();
		this.traceid = traceid;
		this.exceptionMsg = exceptionMsg;
		this.date = this.getDate(traceid);
	}

	public String getTraceid() {
		return traceid;
	}

	public String getExceptionMsg() {
		return exceptionMsg;
	}	

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}
	private Date getDate(String traceid) {
		
		Date date;		
		String[] traceidPartitions = traceid.split("\\.");
		
		try {
			String timeStr = traceidPartitions[2];
			Long timeStamp = Long.parseLong(timeStr);
			date = new Date(timeStamp);
		} catch (Exception e) {
			return null;
		}	
		return date;
	}

}
