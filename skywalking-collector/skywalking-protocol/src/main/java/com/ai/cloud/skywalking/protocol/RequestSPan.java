package com.ai.cloud.skywalking.protocol;

import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.protocol.common.CallType;
import com.ai.cloud.skywalking.protocol.common.SpanType;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wusheng on 16/7/4.
 */
public class RequestSpan extends AbstractDataSerializable {
    /**
     * tid，调用链的全局唯一标识
     */
    private String traceId;
    /**
     * 当前调用链的上级描述<br/>
     * 如当前序号为：0.1.0时，parentLevel=0.1
     */
    private String parentLevel;
    /**
     * 当前调用链的本机描述<br/>
     * 如当前序号为：0.1.0时，levelId=0
     */
    private int levelId = 0;
    /**
     * 调用链中单个节点的入口描述<br/>
     * 如：java方法名，调用的RPC地址等等
     */
    private String viewPointId = "";
    /**
     * 节点调用开始时间
     */
    private long startDate = System.currentTimeMillis();
    /**
     * 节点调用的发生机器描述<br/>
     * 包含机器名 + IP地址
     */
    private String address = "";

    /**
     * 节点类型描述<br/>
     * 已字符串的形式描述<br/>
     * 如：java,dubbo等
     */
    private String spanTypeDesc = "";

    /**
     * 节点调用类型描述<br/>
     *
     * @see CallType
     */
    private String callType = "";

    /**
     * 节点分布式类型<br/>
     * 本地调用 / RPC服务端 / RPC客户端
     */
    private SpanType spanType = SpanType.LOCAL;

    /**
     * 节点调用的所在进程号
     */
    private String processNo = "";
    /**
     * 节点调用所在的系统逻辑名称<br/>
     * 由授权文件指定
     */
    private String applicationId = "";

    /**
     * 用户id<br/>
     * 由授权文件指定
     */
    private String userId;

    private Map<String, String> paramters = new HashMap<String, String>();

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getParentLevel() {
        return parentLevel;
    }

    public void setParentLevel(String parentLevel) {
        this.parentLevel = parentLevel;
    }

    public int getLevelId() {
        return levelId;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }

    public String getViewPointId() {
        return viewPointId;
    }

    public void setViewPointId(String viewPointId) {
        this.viewPointId = viewPointId;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSpanTypeDesc() {
        return spanTypeDesc;
    }

    public void setSpanTypeDesc(String spanTypeDesc) {
        this.spanTypeDesc = spanTypeDesc;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public SpanType getSpanType() {
        return spanType;
    }

    public void setSpanType(SpanType spanType) {
        this.spanType = spanType;
    }

    public String getProcessNo() {
        return processNo;
    }

    public void setProcessNo(String processNo) {
        this.processNo = processNo;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, String> getParamters() {
        return paramters;
    }

    public void setParamters(Map<String, String> paramters) {
        this.paramters = paramters;
    }

    @Override
    public int getDataType() {
        return 1;
    }

    @Override
    public byte[] getData() {
        return new byte[0];
    }

    @Override
    public boolean isNull() {
        return false;
    }
}
