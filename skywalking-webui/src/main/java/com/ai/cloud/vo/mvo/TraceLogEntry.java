package com.ai.cloud.vo.mvo;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.util.Constants;
import com.ai.cloud.util.common.StringUtil;

/***
 * hbase存储的链路信息模型
 * 
 * @author tz
 * @date 2015年11月18日 下午5:45:14
 * @version V0.1
 */
public class TraceLogEntry extends Span {

	private String colId;

	private long endDate;

	private List<TimeLineEntry> timeLineList = new ArrayList<TimeLineEntry>() {
		private static final long serialVersionUID = 1L;

		@Override
		public String toString() {
			Iterator<TimeLineEntry> it = this.iterator();
			return it.hasNext() ? it.next().toString() : "";
		};
	};

	private String spanTypeStr;

	private String spanTypeName;

	private String statusCodeStr;

	private String statusCodeName;

	private String applicationIdStr;

	private String viewPointIdSub;

	private TraceLogEntry() {

	}

	private TraceLogEntry(String str) {
		super(str);
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

	public void setEndDate(long endDate) {
		this.endDate = endDate;
	}

	public List<TimeLineEntry> getTimeLineList() {
		return timeLineList;
	}

	public void setTimeLineList(List<TimeLineEntry> timeLineList) {
		this.timeLineList = timeLineList;
	}

	public String getSpanTypeStr() {
		return spanTypeStr;
	}

	public void setSpanTypeStr(String spanTypeStr) {
		this.spanTypeStr = spanTypeStr;
	}

	public String getSpanTypeName() {
		return spanTypeName;
	}

	public void setSpanTypeName(String spanTypeName) {
		this.spanTypeName = spanTypeName;
	}

	public String getStatusCodeStr() {
		return statusCodeStr;
	}

	public void setStatusCodeStr(String statusCodeStr) {
		this.statusCodeStr = statusCodeStr;
	}

	public String getStatusCodeName() {
		return statusCodeName;
	}

	public void setStatusCodeName(String statusCodeName) {
		this.statusCodeName = statusCodeName;
	}

	public String getApplicationIdStr() {
		return applicationIdStr;
	}

	public void setApplicationIdStr(String applicationIdStr) {
		this.applicationIdStr = applicationIdStr;
	}

	public String getViewPointIdSub() {
		return viewPointIdSub;
	}

	public void setViewPointIdSub(String viewPointIdSub) {
		this.viewPointIdSub = viewPointIdSub;
	}

	private static TraceLogEntry convert(String str)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		TraceLogEntry result = new TraceLogEntry(str);

		// 处理类型key-value
		String spanTypeStr = String.valueOf(result.getSpanType());
		if (StringUtil.isBlank(spanTypeStr) || Constants.SPAN_TYPE_MAP.containsKey(spanTypeStr)) {
			result.spanTypeStr = Constants.SPAN_TYPE_U;
		}
		String spanTypeName = Constants.SPAN_TYPE_MAP.get(spanTypeStr);
		result.spanTypeStr = spanTypeStr;
		result.spanTypeName = spanTypeName;

		// 处理状态key-value
		String statusCodeStr = String.valueOf(result.getStatusCode());
		if (StringUtil.isBlank(statusCodeStr) || Constants.STATUS_CODE_MAP.containsKey(statusCodeStr)) {
			result.statusCodeStr = Constants.STATUS_CODE_9;
		}
		String statusCodeName = Constants.STATUS_CODE_MAP.get(statusCodeStr);
		result.statusCodeStr = statusCodeStr;
		result.statusCodeName = statusCodeName;

		result.applicationIdStr = result.applicationId;
		if (!StringUtil.isBlank(result.viewPointId) && result.viewPointId.length() > 40) {
			result.viewPointIdSub = result.viewPointId.substring(0, 20) + "..."
					+ result.viewPointId.substring(result.viewPointId.length() - 20);
		} else {
			result.viewPointIdSub = result.viewPointId;
		}

		result.addTimeLine(result.startDate, result.cost);
		result.endDate = result.startDate + result.cost;
		return result;
	}

	/***
	 * 增加时间轴信息
	 * 
	 * @param startDate
	 * @param cost
	 */
	public void addTimeLine(long startDate, long cost) {
		timeLineList.add(new TimeLineEntry(startDate, cost));
	}

	public static TraceLogEntry convert(String str, String colId)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		TraceLogEntry result = convert(str);
		result.colId = colId;
		return result;
	}

	/***
	 * 补充丢失的链路信息
	 * 
	 * @param colId
	 * @return
	 */
	public static TraceLogEntry addLostBuriedPointEntry(String colId) {
		TraceLogEntry result = new TraceLogEntry();
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
		return "TraceLogEntry [colId=" + colId + ", endDate=" + endDate + ", timeLineList=" + timeLineList
				+ ", spanTypeStr=" + spanTypeStr + ", spanTypeName=" + spanTypeName + ", statusCodeStr=" + statusCodeStr
				+ ", statusCodeName=" + statusCodeName + ", applicationIdStr=" + applicationIdStr + ", viewPointIdSub="
				+ viewPointIdSub + ", traceId=" + traceId + ", parentLevel=" + parentLevel + ", levelId=" + levelId
				+ ", viewPointId=" + viewPointId + ", startDate=" + startDate + ", cost=" + cost + ", address="
				+ address + ", statusCode=" + statusCode + ", exceptionStack=" + exceptionStack + ", spanType="
				+ spanType + ", isReceiver=" + isReceiver + ", businessKey=" + businessKey + ", processNo=" + processNo
				+ ", applicationId=" + applicationId + ", originData=" + originData + "]";
	}

}
