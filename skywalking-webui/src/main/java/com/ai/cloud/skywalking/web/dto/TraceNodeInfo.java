package com.ai.cloud.skywalking.web.dto;

import com.ai.cloud.skywalking.protocol.FullSpan;
import com.ai.cloud.skywalking.protocol.RequestSpan;
import com.ai.cloud.skywalking.web.util.Constants;
import com.ai.cloud.skywalking.web.util.StringUtil;
import com.google.protobuf.InvalidProtocolBufferException;

import java.lang.reflect.InvocationTargetException;
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

    private TraceNodeInfo(){
    }

    private TraceNodeInfo(RequestSpan requestSpan) {
        super(requestSpan);
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

    private static TraceNodeInfo convert(byte[] originData)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            InvalidProtocolBufferException {
        TraceNodeInfo result = new TraceNodeInfo(new RequestSpan(originData));

        // 处理类型key-value
        String spanTypeStr = String.valueOf(result.getSpanType());
        if (StringUtil.isBlank(spanTypeStr) || Constants.SPAN_TYPE_MAP.containsKey(spanTypeStr)) {
            result.spanTypeStr = Constants.SPAN_TYPE_U;
        }
        result.spanTypeStr = spanTypeStr;
        if (Constants.SPAN_TYPE_MAP.containsKey(spanTypeStr)) {
            result.spanTypeName = Constants.SPAN_TYPE_MAP.get(spanTypeStr);
            ;
        } else {
            //非默认支持的类型，使用原文中的类型，不需要解析
            result.spanTypeName = result.spanTypeStr;
        }

        // 处理状态key-value
        String statusCodeStr = String.valueOf(result.getStatusCode());
        if (StringUtil.isBlank(statusCodeStr) || Constants.STATUS_CODE_MAP.containsKey(statusCodeStr)) {
            result.statusCodeStr = Constants.STATUS_CODE_9;
        }
        String statusCodeName = Constants.STATUS_CODE_MAP.get(statusCodeStr);
        result.statusCodeStr = statusCodeStr;
        result.statusCodeName = statusCodeName;

        result.applicationIdStr = result.getApplicationId();
        if (!StringUtil.isBlank(result.getViewPointId()) && result.getViewPointId().length() > 60) {
            result.viewPointIdSub = result.getViewPointId().substring(0, 30) + "..." + result.getViewPointId()
                    .substring(result.getViewPointId().length() - 30);
        } else {
            result.viewPointIdSub = result.getViewPointId();
        }

        result.addTimeLine(result.getStartDate(), result.getCost());
        result.endDate = result.getStartDate() + result.getCost();
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

    public static TraceNodeInfo convert(byte[] requestSpanBytes, String colId)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            InvalidProtocolBufferException {
        TraceNodeInfo result = convert(requestSpanBytes);
        result.colId = colId;
        return result;
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
            result.setParentLevel(colId.substring(0, colId.lastIndexOf(Constants.VAL_SPLIT_CHAR)));
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
                + viewPointIdSub + ", traceId=" + getTraceId() + ", parentLevel=" + getParentLevel() + ", levelId=" +
                getLevelId() + ", viewPointId=" + getViewPointId() + ", startDate=" + getStartDate() + ", cost="
                + getCost() + ", statusCode=" + getStatusCode() + ", exceptionStack=" + getExceptionStack()
                + ", spanType=" + getSpanType() + ",  businessKey=" + getBusinessKey() + ", applicationId="
                + getApplicationId() + "]";
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

}
