package com.a.eye.skywalking.web.dto;

import com.a.eye.skywalking.web.util.Constants;
import com.a.eye.skywalking.web.util.StringUtil;
import com.a.eye.skywalking.protocol.AckSpan;
import com.a.eye.skywalking.protocol.FullSpan;
import com.a.eye.skywalking.protocol.RequestSpan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TraceNodeInfo extends FullSpan {

    private String colId;

    private long endDate;

    private List<TimeLineEntry> timeLineList = new ArrayList<TimeLineEntry>() {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            Iterator<TimeLineEntry> it = this.iterator();
            return it.hasNext() ? it.next().toString() : "";
        }

        ;
    };

    private String spanTypeStr;

    private String spanTypeName;

    private String statusCodeStr;

    private String statusCodeName;

    private String applicationIdStr;

    private String viewPointIdSub;

    private String serverExceptionStr;

    private TraceNodeInfo() {

    }

    public TraceNodeInfo(RequestSpan requestSpan, AckSpan ackSpan) {
        super(requestSpan, ackSpan);
        this.colId = requestSpan.getParentLevel() == null || requestSpan.getParentLevel().length() == 0 ?
                getLevelId() + "" :
                getParentLevel() + "." + getLevelId();

        // 处理类型key-value
        String spanTypeStr = String.valueOf(requestSpan.getSpanTypeDesc());
        if (StringUtil.isBlank(spanTypeStr) || Constants.SPAN_TYPE_MAP.containsKey(spanTypeStr)) {
            this.spanTypeStr = Constants.SPAN_TYPE_U;
        }
        this.spanTypeStr = spanTypeStr;
        if (Constants.SPAN_TYPE_MAP.containsKey(spanTypeStr)) {
            this.spanTypeName = Constants.SPAN_TYPE_MAP.get(spanTypeStr);
        } else {
            //非默认支持的类型，使用原文中的类型，不需要解析
            this.spanTypeName = this.spanTypeStr;
        }

        // 处理状态key-value
        String statusCodeStr = String.valueOf(getStatusCode());
        if (StringUtil.isBlank(statusCodeStr) || Constants.STATUS_CODE_MAP.containsKey(statusCodeStr)) {
            this.statusCodeStr = Constants.STATUS_CODE_9;
        }
        String statusCodeName = Constants.STATUS_CODE_MAP.get(statusCodeStr);
        this.statusCodeStr = statusCodeStr;
        this.statusCodeName = statusCodeName;

        this.applicationIdStr = this.applicationId;
        if (!StringUtil.isBlank(this.viewPointId) && this.viewPointId.length() > 60) {
            this.viewPointIdSub = this.viewPointId.substring(0, 30) + "..." + this.viewPointId
                    .substring(this.viewPointId.length() - 30);
        } else {
            this.viewPointIdSub = this.viewPointId;
        }

        this.addTimeLine(this.startDate, this.cost);
        this.endDate = this.startDate + this.cost;
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

    public String getStatusCodeStr() {
        return statusCodeStr;
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
    /***
     * 增加时间轴信息
     *
     * @param startDate
     * @param cost
     */
    public void addTimeLine(long startDate, long cost) {
        timeLineList.add(new TimeLineEntry(startDate, cost));
    }


    /***
     * 补充丢失的链路信息
     *
     * @param colId
     * @return
     */
    public static TraceNodeInfo addLostBuriedPointEntry(String colId) {
        TraceNodeInfo result = new TraceNodeInfo();
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
        return "TraceNodeInfo [colId=" + colId + ", endDate=" + endDate + ", timeLineList=" + timeLineList
                + ", spanTypeStr=" + spanTypeStr + ", spanTypeName=" + spanTypeName + ", statusCodeStr=" + statusCodeStr
                + ", statusCodeName=" + statusCodeName + ", applicationIdStr=" + applicationIdStr + ", viewPointIdSub="
                + viewPointIdSub + ", traceId=" + traceId + ", parentLevel=" + parentLevel + ", levelId=" + levelId
                + ", viewPointId=" + viewPointId + ", startDate=" + startDate + ", cost=" + cost + ", address="
                + address + ", statusCode=" + statusCode + ", exceptionStack=" + exceptionStack + ", spanType="
                + spanType + ", businessKey=" + businessKey + ", processNo=" + processNo + ", applicationId="
                + applicationId + "]";
    }

    public List<TimeLineEntry> getTimeLineList() {
        return timeLineList;
    }

    public String getSpanTypeStr() {
        return spanTypeStr;
    }

    public String getSpanTypeName() {
        return spanTypeName;
    }

    public String getStatusCodeName() {
        return statusCodeName;
    }

    public String getServerExceptionStr() {
        return serverExceptionStr;
    }

    public void setServerExceptionStr(String serverExceptionStr) {
        this.serverExceptionStr = serverExceptionStr;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setExceptionStack(String exceptionStack) {
        this.exceptionStack = exceptionStack;
    }
}
