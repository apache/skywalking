package com.a.eye.skywalking.model;


import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.network.model.Tag;
import com.a.eye.skywalking.util.RoutingKeyGenerator;
import com.a.eye.skywalking.util.StringUtil;
import com.a.eye.skywalking.util.TraceIdGenerator;

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
    protected TraceId traceId;
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
     * 节点类型<br/>
     * 如：RPC Client,RPC Server,Local
     */
    private String spanType = SpanType.LOCAL;

    /**
     * 业务字段<br/>
     */
    private String businessKey = "";

    private int routeKey;

    private Map<String, String> tags = new HashMap<String, String>();
    private String viewPoint;

    public Span(String operationName) {
        this(TraceIdGenerator.generate(), "", 0, operationName, RoutingKeyGenerator.generate(operationName));
    }

    public Span(Span parentSpan, String operationName) {
        this(parentSpan.traceId, parentSpan.generateParentLevelId(), 0, operationName, parentSpan.getRouteKey());
    }

    public Span(ContextData contextData, String operationName) {
        this(contextData.getTraceId(), contextData.getParentLevel(), contextData.getLevelId(), operationName, contextData.getRouteKey());
    }

    private Span(TraceId traceId, String parentLevel, int levelId, String operationName, int routeKey) {
        this.traceId = traceId;
        this.parentLevel = parentLevel;
        this.levelId = levelId;
        this.routeKey = routeKey;
        this.startDate = System.currentTimeMillis();

        this.setTag(Tag.USER_NAME, Config.SkyWalking.USERNAME);
        this.setTag(Tag.APPLICATION_CODE, Config.SkyWalking.APPLICATION_CODE);
        this.setTag(Tag.VIEW_POINT, operationName);
    }

    public TraceId getTraceId() {
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

    public String getSpanType() {
        return spanType;
    }

    public void setTag(Tag key, String value) {
        if (value != null)
            this.tags.put(key.toString(), value);
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

    public RequestSpan.Builder buildRequestSpan(RequestSpan.Builder builder) {
        return builder.setTraceId(this.traceId).setParentLevel(this.parentLevel).setLevelId(this.levelId)
                .setStartDate(this.startDate).setRouteKey(routeKey).putAllTags(tags);
    }

    public AckSpan.Builder buildAckSpan(AckSpan.Builder builder) {
        return builder.setTraceId(this.traceId).setParentLevel(this.parentLevel).setLevelId(this.levelId)
                .setCost(System.currentTimeMillis() - this.startDate).setRouteKey(routeKey).putAllTags(tags);
    }

    public int getRouteKey() {
        return routeKey;
    }

    private String generateParentLevelId() {
        if (!StringUtil.isEmpty(this.getParentLevel())) {
            return this.getParentLevel() + "." + this.getLevelId();
        } else {
            return String.valueOf(this.getLevelId());
        }
    }

    public String getViewPoint() {
        return viewPoint;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }
}
