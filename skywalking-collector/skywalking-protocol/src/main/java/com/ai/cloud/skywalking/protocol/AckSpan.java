package com.ai.cloud.skywalking.protocol;

import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.protocol.exception.ConvertFailedException;
import com.ai.cloud.skywalking.protocol.proto.TraceProtocol;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wusheng on 16/7/4.
 */
public class AckSpan extends AbstractDataSerializable {
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
    private int    levelId        = 0;
    /**
     * 节点调用花费时间
     */
    private long   cost           = 0L;
    /**
     * 节点调用的状态<br/>
     * 0：成功<br/>
     * 1：异常<br/>
     * 异常判断原则：代码产生exception，并且此exception不在忽略列表中
     */
    private byte   statusCode     = 0;
    /**
     * 节点调用的错误堆栈<br/>
     * 堆栈以JAVA的exception为主要判断依据
     */
    private String exceptionStack = "";

    /**
     * 埋点入参列表,补充时触发
     */
    private Map<String, String> paramters = new HashMap<String, String>();


    private String viewPointId;

    private String userId;

    private String applicationId;

    public AckSpan(Span spanData) {
        this.traceId = spanData.getTraceId();
        this.parentLevel = spanData.getParentLevel();
        this.levelId = spanData.getLevelId();
        this.cost = System.currentTimeMillis() - spanData.getStartDate();
        this.statusCode = spanData.getStatusCode();
        this.exceptionStack = spanData.getExceptionStack();
        this.userId = spanData.getUserId();
        this.applicationId = spanData.getApplicationId();
        this.paramters.putAll(spanData.getParameters());
    }

    public AckSpan() {

    }

    public AckSpan(byte[] originData) throws InvalidProtocolBufferException {
        TraceProtocol.AckSpan ackSpanProtocol = TraceProtocol.AckSpan.parseFrom(originData);
        this.setTraceId(ackSpanProtocol.getTraceId());
        this.setParentLevel(ackSpanProtocol.getParentLevel());
        this.setLevelId(ackSpanProtocol.getLevelId());
        this.setCost(ackSpanProtocol.getCost());
        this.setExceptionStack(ackSpanProtocol.getExceptionStack());
        this.setStatusCode((byte) ackSpanProtocol.getStatusCode());
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

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(byte statusCode) {
        this.statusCode = statusCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public void setExceptionStack(String exceptionStack) {
        this.exceptionStack = exceptionStack;
    }

    public Map<String, String> getParamters() {
        return paramters;
    }

    public void setParamters(Map<String, String> paramters) {
        this.paramters = paramters;
    }

    @Override
    public int getDataType() {
        return 2;
    }

    @Override
    public byte[] getData() {
        return TraceProtocol.AckSpan.newBuilder().setTraceId(traceId).setParentLevel(parentLevel).
                setLevelId(levelId).setCost(cost).setStatusCode(statusCode).setExceptionStack(exceptionStack).build()
                .toByteArray();
    }

    @Override
    public AbstractDataSerializable convertData(byte[] data) throws ConvertFailedException {
        AckSpan ackSpan = new AckSpan();
        try {
            TraceProtocol.AckSpan ackSpanProtocol = TraceProtocol.AckSpan.parseFrom(data);
            ackSpan.setTraceId(ackSpanProtocol.getTraceId());
            ackSpan.setParentLevel(ackSpanProtocol.getParentLevel());
            ackSpan.setLevelId(ackSpanProtocol.getLevelId());
            ackSpan.setCost(ackSpanProtocol.getCost());
            ackSpan.setExceptionStack(ackSpanProtocol.getExceptionStack());
            ackSpan.setStatusCode((byte) ackSpanProtocol.getStatusCode());
        } catch (InvalidProtocolBufferException e) {
            throw new ConvertFailedException();
        }

        return ackSpan;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    public String getUserId() {
        return userId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getViewPointId() {
        return viewPointId;
    }
}
