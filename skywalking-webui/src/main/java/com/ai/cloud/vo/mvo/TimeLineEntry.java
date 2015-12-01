/**
 * 
 */
package com.ai.cloud.vo.mvo;

/**
 * 
 * 支持当前节点为RPC调用时，可以在一个tr中展现多个调用时间轴
 * 
 * @author tz
 * @date 2015年11月19日 下午3:45:31
 * @version V0.1
 */
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
