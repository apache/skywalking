package com.a.eye.skywalking.protocol;

import com.a.eye.skywalking.protocol.common.SpanType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Span {
    private final static String INVOKE_RESULT_PARAMETER_KEY = "_ret";

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
    protected byte   statusCode     = 0;
    /**
     * 节点调用的错误堆栈<br/>
     * 堆栈以JAVA的exception为主要判断依据
     */
    protected String exceptionStack = "";

    /**
     * 节点类型<br/>
     * 如：RPC Client,RPC Server,Local
     */
    private   SpanType            spanType   = SpanType.LOCAL;

    /**
     * 业务字段<br/>
     */
    private String businessKey = "";
    /**
     * 应用编码
     */
    private String applicationId;
    /**
     * 归属用户
     */
    private String userId;
    private String viewPointId;

    public Span(String traceId, String applicationId, String userId) {
        this.traceId = traceId;
        this.applicationId = applicationId;
        this.userId = userId;
        this.parentLevel = "";
    }

    public Span(String traceId, String parentLevel, int levelId, String applicationId, String userId) {
        this.traceId = traceId;
        this.parentLevel = parentLevel;
        this.levelId = levelId;
        this.applicationId = applicationId;
        this.userId = userId;
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

    public byte getStatusCode() {
        return statusCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public void setExceptionStack(String exceptionStack) {
        this.exceptionStack = exceptionStack;
    }

    public void setSpanType(SpanType spanType) {
        this.spanType = spanType;
    }

    public SpanType getSpanType() {
        return spanType;
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

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
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


    public void setViewPointId(String viewPointId) {
        this.viewPointId = viewPointId;
    }



    public String getViewPointId() {
        return viewPointId;
    }
}
