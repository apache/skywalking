package com.ai.cloud.vo.mvo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.ai.cloud.util.Constants;

/***
 * hbase存储的链路信息模型
 * 
 * @author tz
 * @date 2015年11月18日 下午5:45:14
 * @version V0.1
 */
public class BuriedPointEntry {
	private String traceId;
	private String parentLevel;
	private int levelId;
	private String viewPointId;
	private long startDate;
	private long cost;
	private String address;
	private char statusCode = Constants.STATUS_CODE_9;
	private String exceptionStack;
	private char spanType;
	private boolean isReceiver = false;
	private String businessKey;
	private String processNo;

	private String colId;
	private long endDate;
	private List<TimeLineEntry> timeLineList = new ArrayList<TimeLineEntry>();

	private BuriedPointEntry() {

	}

	public String getTraceId() {
		return traceId;
	}

	public String getParentLevel() {
		return parentLevel;
	}

	public int getLevelId() {
		return levelId;
	}

	public String getViewPointId() {
		return viewPointId;
	}

	public long getStartDate() {
		return startDate;
	}

	public long getCost() {
		return cost;
	}

	public String getAddress() {
		return address;
	}

	public char getStatusCode() {
		return statusCode;
	}

	public String getExceptionStack() {
		return exceptionStack;
	}

	public char getSpanType() {
		return spanType;
	}

	public boolean isReceiver() {
		return isReceiver;
	}

	public String getBusinessKey() {
		return businessKey;
	}

	public String getProcessNo() {
		return processNo;
	}

	public String getColId() {
		return colId;
	}

	public void setColId(String colId) {
		this.colId = colId;
	}
	
	public long getEndDate() {
		return endDate;
	}

	public List<TimeLineEntry> getTimeLineList() {
		return timeLineList;
	}

	private static BuriedPointEntry convert(String str) {
		BuriedPointEntry result = new BuriedPointEntry();
		String[] fieldValues = str.split("\\^\\~");
		result.traceId = fieldValues[0].trim();
		result.parentLevel = fieldValues[1].trim();
		result.levelId = Integer.valueOf(fieldValues[2]);
		result.viewPointId = fieldValues[3].trim();
		result.startDate = Long.valueOf(fieldValues[4]);
		result.cost = Long.parseLong(fieldValues[5]);
		result.address = fieldValues[6].trim();
		result.statusCode = fieldValues[7].charAt(0);
		result.exceptionStack = fieldValues[8].trim();
		result.spanType = fieldValues[9].charAt(0);
		result.isReceiver = Boolean.getBoolean(fieldValues[10]);
		result.businessKey = fieldValues[11].trim();
		result.processNo = fieldValues[12].trim();
		return result;
	}
	
	/***
	 * 增加时间轴信息
	 * @param startDate
	 * @param cost
	 */
	public void addTimeLine(long startDate, long cost) {
		timeLineList.add(new TimeLineEntry(startDate, cost));
	}

	public static BuriedPointEntry convert(String str, String colId) {
		BuriedPointEntry result = convert(str);
		result.addTimeLine(result.startDate, result.cost);
		result.colId = colId;
		result.endDate = result.startDate + result.cost;
		return result;
	}

	/***
	 * 补充丢失的链路信息
	 * 
	 * @param colId
	 * @return
	 */
	public static BuriedPointEntry addLostBuriedPointEntry(String colId) {
		BuriedPointEntry result = new BuriedPointEntry();
		result.colId = colId;
		if (colId.indexOf(Constants.VAL_SPLIT_CHAR) > -1) {
			result.parentLevel = colId.substring(0, colId.lastIndexOf(Constants.VAL_SPLIT_CHAR));
		} else {
			result.parentLevel = "";
		}
		result.timeLineList.add(new TimeLineEntry());

		// 其它默认值
		return result;
	}

	@Override
	public String toString() {
		return "BuriedPointEntry [colId=" + colId + ", traceId=" + traceId + ", parentLevel=" + parentLevel
				+ ", levelId=" + levelId + ", viewPointId=" + viewPointId + ", startDate=" + startDate + ", cost="
				+ cost + ", address=" + address + ", statusCode=" + statusCode + ", exceptionStack=" + exceptionStack
				+ ", spanType=" + spanType + ", isReceiver=" + isReceiver + ", businessKey=" + businessKey
				+ ", processNo=" + processNo + "]";
	}

}
