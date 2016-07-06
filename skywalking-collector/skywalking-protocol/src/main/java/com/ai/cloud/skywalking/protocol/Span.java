package com.ai.cloud.skywalking.protocol;

import com.ai.cloud.skywalking.protocol.common.SpanType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Span {
    private Logger logger = Logger.getLogger(Span.class.getName());
    /**
     * tid，调用链的全局唯一标识
     */
    protected String traceId;
    /**
     * 当前调用链的上级描述<br/>
     * 如当前序号为：0.1.0时，parentLevel=0.1
     */
    protected String parentLevel;
    /**
     * 当前调用链的本机描述<br/>
     * 如当前序号为：0.1.0时，levelId=0
     */
    protected int levelId = 0;

    /**
     * 节点调用开始时间
     */
    protected long startDate = System.currentTimeMillis();

    /**
     * 节点调用的状态<br/>
     * 0：成功<br/>
     * 1：异常<br/>
     * 异常判断原则：代码产生exception，并且此exception不在忽略列表中
     */
    protected byte statusCode = 0;
    /**
     * 节点调用的错误堆栈<br/>
     * 堆栈以JAVA的exception为主要判断依据
     */
    protected String exceptionStack = "";

    /**
     * 节点的状态<br/>
     * 不参与序列化
     */
    protected boolean isInvalidate = false;

    /**
     * 节点调用过程中的业务字段<br/>
     * 如：业务系统设置的订单号，SQL语句等
     */
    protected Map<String, String> parameters = new HashMap<String, String>();
    /**
     * 节点类型<br/>
     * 如：RPC Client,RPC Server,Local
     */
    private SpanType spanType = SpanType.LOCAL;

    public Span(String traceId) {
        this.traceId = traceId;
    }

    public Span(String traceId, String parentLevel, int levelId) {
        this.traceId = traceId;
        this.parentLevel = parentLevel;
        this.levelId = levelId;
    }

    public String getTraceId() {
        return traceId;
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

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public boolean isInvalidate() {
        return isInvalidate;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public void setExceptionStack(String exceptionStack) {
        this.exceptionStack = exceptionStack;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public void setInvalidate(boolean invalidate) {
        isInvalidate = invalidate;
    }

    public boolean isRPCClientSpan() {
        if (spanType == SpanType.RPC_CLIENT) {
            return true;
        }
        return false;
    }

    public void setSpanType(SpanType spanType) {
        this.spanType = spanType;
    }

    public SpanType getSpanType() {
        return spanType;
    }

    public void setIsInvalidate(boolean isInvalidate) {
        this.isInvalidate = isInvalidate;
    }

    public void handleException(Throwable e, Set<String> exclusiveExceptionSet, int maxExceptionStackLength) {
        ByteArrayOutputStream buf = null;
        StringBuilder expMessage = new StringBuilder();
        try {
            buf = new ByteArrayOutputStream();
            Throwable causeException = e;
            while (expMessage.length() < maxExceptionStackLength && causeException != null) {
                causeException.printStackTrace(new java.io.PrintWriter(buf, true));
                expMessage.append(buf.toString());
                causeException = causeException.getCause();
            }

        } finally {
            try {
                buf.close();
            } catch (IOException ioe) {
                logger.log(Level.ALL, "Close exception stack input stream failed", ioe);
            }
        }

        int sublength = maxExceptionStackLength;
        if (maxExceptionStackLength > expMessage.length()) {
            sublength = expMessage.length();
        }

        this.exceptionStack = expMessage.toString().substring(0, sublength);

        if (!exclusiveExceptionSet.contains(e.getClass().getName())) {
            this.statusCode = 1;
        }
    }


}
