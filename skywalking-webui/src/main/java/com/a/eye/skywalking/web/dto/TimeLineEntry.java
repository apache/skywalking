/**
 * 
 */
package com.a.eye.skywalking.web.dto;

public class TimeLineEntry {
	
	private long startTime = 0l;
	
	private long cost = 0l;
	
	public TimeLineEntry(){
	}
	
	public TimeLineEntry(long startTime, long cost){
		this.startTime = startTime;
		this.cost = cost;
	}
	
	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getCost() {
		return cost;
	}

	public void setCost(long cost) {
		this.cost = cost;
	}

	@Override
	public String toString() {
		return "TimeLineEntry [startTime=" + startTime + ", cost=" + cost + "]";
	}
}
