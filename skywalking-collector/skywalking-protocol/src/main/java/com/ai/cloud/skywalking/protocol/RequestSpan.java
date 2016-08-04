package com.ai.cloud.skywalking.protocol;

import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.protocol.common.CallType;
import com.ai.cloud.skywalking.protocol.common.SpanType;
import com.ai.cloud.skywalking.protocol.exception.ConvertFailedException;
import com.ai.cloud.skywalking.protocol.proto.TraceProtocol;
import com.google.protobuf.InvalidProtocolBufferException;

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
    private int    levelId     = 0;
    /**
     * 调用链中单个节点的入口描述<br/>
     * 如：java方法名，调用的RPC地址等等
     */
    private String viewPointId = "";
    /**
     * 节点调用开始时间
     */
    private long   startDate   = System.currentTimeMillis();

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
     * 节点调用所在的系统逻辑名称<br/>
     * 由授权文件指定
     */
    private String applicationId = "";

    /**
     * 用户id<br/>
     * 由授权文件指定
     */
    private String userId = "";

    /**
     * 埋点入参列表
     */
    private Map<String, String> parameters = new HashMap<String, String>();

    /**
     * 业务字段
     */
    private String businessKey = "";

    /**
     * 实例ID
     */
    private String agentId = "";

    public RequestSpan(Span spanData) {
        this.traceId = spanData.getTraceId();
        this.parentLevel = spanData.getParentLevel();
        this.levelId = spanData.getLevelId();
        this.spanType = spanData.getSpanType();
        this.applicationId = spanData.getApplicationId();
        this.userId = spanData.getUserId();
    }

    public RequestSpan() {

    }

    public RequestSpan(byte[] originData) throws InvalidProtocolBufferException {
        TraceProtocol.RequestSpan requestSpanByte = TraceProtocol.RequestSpan.parseFrom(originData);
        this.setTraceId(requestSpanByte.getTraceId());
        this.setParentLevel(requestSpanByte.getParentLevel());
        this.setLevelId(requestSpanByte.getLevelId());
        this.setApplicationId(requestSpanByte.getApplicationId());
        this.setCallType(requestSpanByte.getCallType());
        this.setSpanType(SpanType.convert(requestSpanByte.getSpanType()));
        this.setSpanTypeDesc(requestSpanByte.getSpanTypeDesc());
        this.setStartDate(requestSpanByte.getStartDate());
        this.setUserId(requestSpanByte.getUserId());
        this.setViewPointId(requestSpanByte.getViewPointId());
        this.setBusinessKey(requestSpanByte.getBussinessKey());
        this.setAgentId(requestSpanByte.getAgentId());
    }

    private boolean isEntrySpan() {
        return "0".equals(this.getParentLevel() + this.getLevelId());
    }

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

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public int getDataType() {
        return 1;
    }

    @Override
    public byte[] getData() {
        TraceProtocol.RequestSpan.Builder builder =
                TraceProtocol.RequestSpan.newBuilder().setTraceId(traceId).setParentLevel(parentLevel)
                        .setLevelId(levelId).setViewPointId(viewPointId).setStartDate(startDate)
                        .setSpanType(spanType.getValue()).setSpanTypeDesc(spanTypeDesc);
        if (businessKey != null && businessKey.length() > 0) {
            builder.setBussinessKey(businessKey);
        }

        if (parameters != null && parameters.size() > 0) {
            builder.getParametersMap().putAll(parameters);
        }

        return builder.setCallType(callType).setApplicationId(applicationId).setUserId(userId).setAgentId(agentId)
                .build().toByteArray();
    }

    @Override
    public AbstractDataSerializable convertData(byte[] data) throws ConvertFailedException {
        RequestSpan requestSpan = new RequestSpan();
        try {
            TraceProtocol.RequestSpan requestSpanByte = TraceProtocol.RequestSpan.parseFrom(data);
            requestSpan.setTraceId(requestSpanByte.getTraceId());
            requestSpan.setParentLevel(requestSpanByte.getParentLevel());
            requestSpan.setLevelId(requestSpanByte.getLevelId());
            requestSpan.setApplicationId(requestSpanByte.getApplicationId());
            requestSpan.setCallType(requestSpanByte.getCallType());
            requestSpan.setSpanType(SpanType.convert(requestSpanByte.getSpanType()));
            requestSpan.setSpanTypeDesc(requestSpanByte.getSpanTypeDesc());
            requestSpan.setStartDate(requestSpanByte.getStartDate());
            requestSpan.setUserId(requestSpanByte.getUserId());
            requestSpan.setViewPointId(requestSpanByte.getViewPointId());
            requestSpan.setBusinessKey(requestSpanByte.getBussinessKey());
            requestSpan.setAgentId(requestSpanByte.getAgentId());
        } catch (InvalidProtocolBufferException e) {
            throw new ConvertFailedException();
        }

        return requestSpan;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public static class RequestSpanBuilder {
        private RequestSpan ackSpan;

        private RequestSpanBuilder(Span span) {
            ackSpan = new RequestSpan(span);
        }

        public static RequestSpanBuilder newBuilder(Span span) {
            return new RequestSpanBuilder(span);
        }

        public RequestSpanBuilder applicationId(String applicationId) {
            ackSpan.applicationId = applicationId;
            return this;
        }

        public RequestSpanBuilder callType(String callType) {
            ackSpan.callType = callType;
            return this;
        }

        public RequestSpanBuilder spanTypeDesc(String spanTypeDesc) {
            ackSpan.spanTypeDesc = spanTypeDesc;
            return this;
        }

        public RequestSpanBuilder userId(String userId) {
            ackSpan.userId = userId;
            return this;
        }

        public RequestSpanBuilder bussinessKey(String bussinessKey) {
            ackSpan.businessKey = bussinessKey;
            return this;
        }

        public RequestSpanBuilder parameters(Map<String, String> parameters) {
            if (ackSpan.isEntrySpan()) {
                ackSpan.parameters = parameters;
            }

            return this;
        }

        public RequestSpan build() {
            return ackSpan;
        }

        public RequestSpanBuilder viewPoint(String viewPoint) {
            ackSpan.viewPointId = viewPoint;
            return this;
        }
    }

    public String getBusinessKey() {
        return businessKey;
    }

}
